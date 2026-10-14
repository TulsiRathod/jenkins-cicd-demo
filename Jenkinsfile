// =====================================================================
//  CI/CD pipeline for all services in this repo
//  Developer pushes -> BUILD -> UNIT TEST -> DEPLOY (dev / uat / prod)
//
//  Parameters:
//    SERVICE - empty = build ALL services, or one service name
//    BRANCH  - Git branch to build (default: main)
//    ENV     - environment to deploy to: dev (default), uat, prod
// =====================================================================
import groovy.transform.Field

// Every service in this repo: folder name -> host port per environment
// (built in this order: backends first, then the frontend that calls them)
@Field final Map SERVICES = [
    'inventory': [dev: 8081, uat: 8083, prod: 8085],
    'order':     [dev: 8082, uat: 8084, prod: 8086],
    'frontend':  [dev: 9001, uat: 9002, prod: 9003],
]

// Secrets a service gets at deploy time: env variable -> Jenkins credentials ID
// (the value lives in Jenkins > Credentials, never in Git)
@Field final Map SECRETS = [
    'order': ['PAYMENT_API_KEY': 'payment-api-key'],
]

// Filled in during the run
@Field List selected = []     // services this build works on
@Field Map versions = [:]     // service -> version, e.g. 'order' -> '1.0.7-a1b2c3d'

pipeline {
    agent any

    parameters {
        string(name: 'SERVICE', defaultValue: '',
               description: 'Service to build: inventory, order or frontend. Leave EMPTY to build ALL services.')
        string(name: 'BRANCH', defaultValue: 'main',
               description: 'Git branch to build')
        choice(name: 'ENV', choices: ['dev', 'uat', 'prod'],
               description: 'Environment to deploy to (automatic builds after a push go to dev)')
    }

    // Check GitHub for new commits every minute (no public URL / webhook needed)
    triggers {
        pollSCM('* * * * *')
    }

    options {
        skipDefaultCheckout()     // we check out the BRANCH parameter ourselves
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 1, unit: 'HOURS')
    }

    environment {
        DOCKER_NETWORK = 'cicd'
    }

    stages {

        // ---------- 1. BUILD: checkout, version, compile + package the jar ----------
        stage('Build') {
            steps {
                script {
                    def branch = params.BRANCH?.trim() ?: 'main'
                    checkout([$class: 'GitSCM',
                              branches: [[name: "*/${branch}"]],
                              userRemoteConfigs: scm.userRemoteConfigs])

                    def requested = params.SERVICE?.trim()
                    if (!requested) {
                        selected = SERVICES.keySet() as List
                    } else if (SERVICES.containsKey(requested)) {
                        selected = [requested]
                    } else {
                        error "Unknown service '${requested}'. Valid: ${SERVICES.keySet().join(', ')} (or empty for all)"
                    }
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${requested ?: 'all services'} @ ${branch} -> ${targetEnv()}"

                    // Version = VERSION file + build number + git commit, e.g. 1.0.7-a1b2c3d
                    def sha = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    for (svc in selected) {
                        versions[svc] = "${readFile("${svc}/VERSION").trim()}.${env.BUILD_NUMBER}-${sha}".toString()
                        echo "Building ${svc} v${versions[svc]}"
                        dir(svc) {
                            sh "mvn -B -Drevision=${versions[svc]} clean package -DskipTests"
                        }
                        archiveArtifacts artifacts: "${svc}/target/*.jar", fingerprint: true
                    }
                    currentBuild.description = versions.collect { svc, v -> "${svc} v${v}" }.join(' | ')
                }
            }
        }

        // ---------- 2. UNIT TEST: tests + coverage gate ----------
        stage('Unit Test') {
            steps {
                script {
                    for (svc in selected) {
                        dir(svc) {
                            sh "mvn -B -Drevision=${versions[svc]} test jacoco:check@coverage-check"
                        }
                    }
                }
            }
            post {
                always {
                    script {
                        // only this build's services (other folders may hold old reports)
                        def reports = selected.collect { svc -> "${svc}/target/surefire-reports/*.xml" }.join(',')
                        junit allowEmptyResults: true, testResults: reports
                        for (svc in selected) {
                            recordCoverage(id: "coverage-${svc}", name: "Coverage: ${svc}",
                                           tools: [[parser: 'JACOCO', pattern: "${svc}/target/site/jacoco/jacoco.xml"]],
                                           sourceDirectories: [[path: "${svc}/src/main/java"]])
                        }
                    }
                }
            }
        }

        // ---------- 3. DEPLOY: Docker image + run container in ENV + smoke test ----------
        stage('Deploy') {
            steps {
                script {
                    for (svc in selected) {
                        sh "docker build -t ${svc}:${versions[svc]} -t ${svc}:latest ${svc}"
                        deployWithRollback(svc, targetEnv(), versions[svc])
                    }
                    currentBuild.description = "Deployed to ${targetEnv()}: " + currentBuild.description
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline finished successfully for: ${selected.join(', ')}"
        }
        failure {
            echo 'Pipeline FAILED - nothing after the failed stage was run, the running version is untouched.'
        }
    }
}

// Environment chosen in the ENV dropdown (dev if the parameter is not set yet)
def targetEnv() {
    return params.ENV ?: 'dev'
}

// Deploy a new version; if it does not come up healthy, put the previous version back
def deployWithRollback(String svc, String envName, String version) {
    def container = "${svc}-${envName}"
    def previous = sh(script: "docker inspect -f '{{.Config.Image}}' ${container} 2>/dev/null || true",
                      returnStdout: true).trim()     // e.g. inventory:1.0.13-aa67115, empty on first deploy

    deployTo(svc, envName, version)
    try {
        smokeTest(svc, envName, version)
    } catch (err) {
        if (!previous || previous == "${svc}:${version}") {
            throw err       // nothing to roll back to
        }
        def previousVersion = previous.substring(previous.indexOf(':') + 1)
        echo "ROLLBACK: ${svc} v${version} is not healthy - putting v${previousVersion} back"
        deployTo(svc, envName, previousVersion)
        smokeTest(svc, envName, previousVersion)
        currentBuild.description = "ROLLED BACK ${svc} in ${envName} to v${previousVersion} | ${currentBuild.description ?: ''}"
        error "Deploy of ${svc} v${version} failed - rolled back to v${previousVersion}"
    }
}

// Replace the running container of a service in an environment with the given image version
def deployTo(String svc, String envName, String version) {
    def port = SERVICES[svc][envName]

    // Secrets from Jenkins Credentials -> environment variables; Jenkins masks them as **** in the log
    def secrets = SECRETS[svc] ?: [:]
    def bindings = []
    def secretFlags = ''
    for (name in secrets.keySet()) {
        bindings << string(credentialsId: secrets[name], variable: name)
        secretFlags += " -e ${name}=\"\$${name}\""
    }

    echo "Deploying ${svc} v${version} to ${envName} (http://localhost:${port})"
    withCredentials(bindings) {
        sh """
            docker network inspect ${env.DOCKER_NETWORK} > /dev/null 2>&1 || docker network create ${env.DOCKER_NETWORK}
            docker rm -f ${svc}-${envName} > /dev/null 2>&1 || true
            docker run -d --name ${svc}-${envName} \
                --network ${env.DOCKER_NETWORK} \
                -p ${port}:8080 \
                -e APP_ENV=${envName}${secretFlags} \
                --restart unless-stopped \
                ${svc}:${version}
        """
    }
}

// Wait until the service is healthy and confirm the expected version is running.
// Fails fast if the container crashes on startup, and prints its last log lines.
def smokeTest(String svc, String envName, String version) {
    withEnv(["CONTAINER=${svc}-${envName}", "EXPECTED=${version}"]) {
        sh '''
            set +x
            echo "Smoke test: waiting for $CONTAINER to be healthy..."
            for i in $(seq 1 30); do
                if curl -fsS "http://$CONTAINER:8080/actuator/health" 2>/dev/null | grep -q UP; then
                    curl -fsS "http://$CONTAINER:8080/version"; echo
                    curl -fsS "http://$CONTAINER:8080/version" | grep -q "$EXPECTED" && exit 0
                    echo "Wrong version running (expected $EXPECTED)"; exit 1
                fi
                if [ "$(docker inspect -f '{{.RestartCount}}' "$CONTAINER")" -gt 0 ]; then
                    echo "$CONTAINER CRASHED on startup. Last log lines:"
                    docker logs --tail 15 "$CONTAINER" 2>&1
                    exit 1
                fi
                sleep 2
            done
            echo "$CONTAINER not healthy after 60 seconds"; exit 1
        '''
    }
    echo "Smoke test passed: ${svc} in ${envName} is running v${version}"
}
