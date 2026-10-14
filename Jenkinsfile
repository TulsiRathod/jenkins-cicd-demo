// =====================================================================
//  CI/CD pipeline for all services in this repo
//  Developer pushes -> BUILD -> UNIT TEST -> DEPLOY
//
//  Parameters:
//    SERVICE - empty = build ALL services, or one service name
//    BRANCH  - Git branch to build (default: main)
// =====================================================================
import groovy.transform.Field

// Every service in this repo: folder name -> host port
@Field final Map SERVICES = [
    'inventory-service': 8081,
    'order-service'    : 8082,
]

// Filled in during the run
@Field List selected = []     // services this build works on
@Field Map versions = [:]     // service -> version, e.g. 'order-service' -> '1.0.7-a1b2c3d'

pipeline {
    agent any

    parameters {
        string(name: 'SERVICE', defaultValue: '',
               description: 'Service to build: inventory-service or order-service. Leave EMPTY to build ALL services.')
        string(name: 'BRANCH', defaultValue: 'main',
               description: 'Git branch to build')
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
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${requested ?: 'all services'} @ ${branch}"

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

        // ---------- 3. DEPLOY: Docker image + run container + smoke test ----------
        stage('Deploy') {
            steps {
                script {
                    for (svc in selected) {
                        sh "docker build -t ${svc}:${versions[svc]} -t ${svc}:latest ${svc}"
                        deployTo(svc, versions[svc])
                        smokeTest(svc, versions[svc])
                    }
                    currentBuild.description = 'Deployed: ' + currentBuild.description
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

// Replace the running container of a service with the given image version
def deployTo(String svc, String version) {
    def port = SERVICES[svc]
    echo "Deploying ${svc} v${version} (http://localhost:${port})"
    sh """
        docker network inspect ${env.DOCKER_NETWORK} > /dev/null 2>&1 || docker network create ${env.DOCKER_NETWORK}
        docker rm -f ${svc} > /dev/null 2>&1 || true
        docker run -d --name ${svc} \
            --network ${env.DOCKER_NETWORK} \
            -p ${port}:8080 \
            -e APP_ENV=production \
            --restart unless-stopped \
            ${svc}:${version}
    """
}

// Wait until the service is healthy and confirm the expected version is running
def smokeTest(String svc, String version) {
    def url = "http://${svc}:8080"
    retry(20) {
        sleep 3
        sh "curl -fsS ${url}/actuator/health | grep -q UP"
    }
    sh "curl -fsS ${url}/version"
    sh "curl -fsS ${url}/version | grep -q '${version}'"
    echo "Smoke test passed: ${svc} is running v${version}"
}
