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
@Field final Map SERVICES = [
    'inventory': [dev: 8081, uat: 8083, prod: 8085],
    'order':     [dev: 8082, uat: 8084, prod: 8086],
]

// Filled in during the run
@Field List selected = []     // services this build works on
@Field Map versions = [:]     // service -> version, e.g. 'order' -> '1.0.7-a1b2c3d'

pipeline {
    agent any

    parameters {
        string(name: 'SERVICE', defaultValue: '',
               description: 'Service to build: inventory or order. Leave EMPTY to build ALL services.')
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
                        deployTo(svc, targetEnv(), versions[svc])
                        smokeTest(svc, targetEnv(), versions[svc])
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

// Replace the running container of a service in an environment with the given image version
def deployTo(String svc, String envName, String version) {
    def port = SERVICES[svc][envName]
    echo "Deploying ${svc} v${version} to ${envName} (http://localhost:${port})"
    sh """
        docker network inspect ${env.DOCKER_NETWORK} > /dev/null 2>&1 || docker network create ${env.DOCKER_NETWORK}
        docker rm -f ${svc}-${envName} > /dev/null 2>&1 || true
        docker run -d --name ${svc}-${envName} \
            --network ${env.DOCKER_NETWORK} \
            -p ${port}:8080 \
            -e APP_ENV=${envName} \
            --restart unless-stopped \
            ${svc}:${version}
    """
}

// Wait until the service is healthy and confirm the expected version is running
def smokeTest(String svc, String envName, String version) {
    def url = "http://${svc}-${envName}:8080"
    retry(20) {
        sleep 3
        sh "curl -fsS ${url}/actuator/health | grep -q UP"
    }
    sh "curl -fsS ${url}/version"
    sh "curl -fsS ${url}/version | grep -q '${version}'"
    echo "Smoke test passed: ${svc} in ${envName} is running v${version}"
}
