// =====================================================================
//  CI/CD pipeline for all services in this repo
//  Developer pushes -> TEST -> PACKAGE -> VERSION -> STAGING -> APPROVAL -> PROD
//
//  SERVICE parameter: empty = build ALL services, or one service name
// =====================================================================
import groovy.transform.Field

// Every service in this repo: folder name -> host ports per environment
@Field final Map SERVICES = [
    'inventory-service': [staging: 8081, production: 8082],
    'order-service'    : [staging: 8083, production: 8084],
]

// Filled in during the run
@Field List selected = []     // services this build works on
@Field Map versions = [:]     // service -> version, e.g. 'order-service' -> '1.0.7-a1b2c3d'

pipeline {
    agent any

    parameters {
        string(name: 'SERVICE', defaultValue: '',
               description: 'Service to build: inventory-service or order-service. Leave EMPTY to build ALL services.')
        choice(name: 'ACTION', choices: ['deploy', 'rollback'],
               description: 'deploy = normal CI/CD run | rollback = put an older version back in production (needs SERVICE)')
        string(name: 'ROLLBACK_VERSION', defaultValue: '',
               description: 'Only for rollback, e.g. 1.0.3-a1b2c3d (see build descriptions in the history)')
    }

    // Check GitHub for new commits every minute (no public URL / webhook needed)
    triggers {
        pollSCM('* * * * *')
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 1, unit: 'HOURS')
    }

    environment {
        DOCKER_NETWORK = 'cicd'
    }

    stages {

        // ---------- 0. WHICH SERVICES? ----------
        stage('Select Services') {
            steps {
                script {
                    def requested = params.SERVICE?.trim()
                    if (!requested) {
                        selected = SERVICES.keySet() as List
                    } else if (SERVICES.containsKey(requested)) {
                        selected = [requested]
                    } else {
                        error "Unknown service '${requested}'. Valid: ${SERVICES.keySet().join(', ')} (or empty for all)"
                    }
                    if (params.ACTION == 'rollback' && selected.size() != 1) {
                        error 'Rollback needs exactly one SERVICE name'
                    }
                    echo "Services in this build: ${selected.join(', ')}"
                }
            }
        }

        // ---------- 1. VERSION: every build gets a unique, traceable version ----------
        stage('Version') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                script {
                    def sha = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    for (svc in selected) {
                        def base = readFile("${svc}/VERSION").trim()
                        versions[svc] = "${base}.${env.BUILD_NUMBER}-${sha}".toString()
                        echo "${svc} will be version ${versions[svc]}"
                    }
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${params.SERVICE?.trim() ?: 'all services'}"
                    currentBuild.description = versions.collect { svc, v -> "${svc} v${v}" }.join(' | ')
                }
            }
        }

        // ---------- 2. TEST: compile + unit & API tests ----------
        stage('Unit Tests') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                script {
                    for (svc in selected) {
                        dir(svc) {
                            sh "mvn -B -Drevision=${versions[svc]} clean test"
                        }
                    }
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '*/target/surefire-reports/*.xml'
                }
            }
        }

        // ---------- 3. QUALITY GATE: fail if coverage is too low ----------
        stage('Code Coverage Gate') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                script {
                    for (svc in selected) {
                        dir(svc) {
                            sh 'mvn -B jacoco:check@coverage-check'
                        }
                    }
                }
            }
            post {
                always {
                    script {
                        for (svc in selected) {
                            recordCoverage(id: "coverage-${svc}", name: "Coverage: ${svc}",
                                           tools: [[parser: 'JACOCO', pattern: "${svc}/target/site/jacoco/jacoco.xml"]],
                                           sourceDirectories: [[path: "${svc}/src/main/java"]])
                        }
                    }
                }
            }
        }

        // ---------- 4. PACKAGE: versioned jar + versioned Docker image ----------
        stage('Package') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                script {
                    for (svc in selected) {
                        dir(svc) {
                            sh "mvn -B -Drevision=${versions[svc]} package -DskipTests"
                        }
                        archiveArtifacts artifacts: "${svc}/target/*.jar", fingerprint: true
                        sh "docker build -t ${svc}:${versions[svc]} -t ${svc}:latest ${svc}"
                    }
                }
            }
        }

        // ---------- 5. DEPLOY TO STAGING + automatic smoke test ----------
        stage('Deploy to Staging') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                script {
                    for (svc in selected) {
                        deployTo(svc, 'staging', versions[svc])
                        smokeTest(svc, 'staging', versions[svc])
                    }
                }
            }
        }

        // ---------- 6. HUMAN APPROVAL GATE ----------
        stage('Approval') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                script {
                    def summary = selected.collect { svc ->
                        "${svc} v${versions[svc]} (staging: http://localhost:${SERVICES[svc].staging})"
                    }.join('\n')
                    timeout(time: 30, unit: 'MINUTES') {
                        input message: "Staging looks good. Deploy to PRODUCTION?\n${summary}",
                              ok: 'Deploy to Production'
                    }
                }
            }
        }

        // ---------- 7. DEPLOY TO PRODUCTION ----------
        stage('Deploy to Production') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                script {
                    for (svc in selected) {
                        deployTo(svc, 'production', versions[svc])
                        smokeTest(svc, 'production', versions[svc])
                    }
                    currentBuild.description = 'In production: ' + currentBuild.description
                }
            }
        }

        // ---------- ROLLBACK: redeploy an image we already built and tested ----------
        stage('Rollback Production') {
            when { expression { params.ACTION == 'rollback' } }
            steps {
                script {
                    def svc = selected[0]
                    def version = params.ROLLBACK_VERSION.trim()
                    if (!version) {
                        error 'ROLLBACK_VERSION is required for a rollback'
                    }
                    sh "docker image inspect ${svc}:${version} > /dev/null"
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ROLLBACK ${svc}"
                    deployTo(svc, 'production', version)
                    smokeTest(svc, 'production', version)
                    currentBuild.description = "In production: ${svc} v${version} (rollback)"
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline finished successfully for: ${selected.join(', ')}"
        }
        failure {
            echo 'Pipeline FAILED - nothing after the failed stage was run, production is untouched.'
        }
    }
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
