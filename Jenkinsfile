// =====================================================================
//  CI/CD pipeline for inventory-service
//  Developer pushes -> TEST -> PACKAGE -> VERSION -> STAGING -> APPROVAL -> PROD
// =====================================================================
pipeline {
    agent any

    parameters {
        choice(name: 'ACTION', choices: ['deploy', 'rollback'],
               description: 'deploy = normal CI/CD run | rollback = put an older version back in production')
        string(name: 'ROLLBACK_VERSION', defaultValue: '',
               description: 'Only for rollback, e.g. 1.0.3-a1b2c3d (see build names in the history)')
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
        IMAGE          = 'inventory-service'
        DOCKER_NETWORK = 'cicd'
    }

    stages {

        // ---------- 1. VERSION: every build gets a unique, traceable version ----------
        stage('Version') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                script {
                    def base = readFile('VERSION').trim()
                    def sha  = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.APP_VERSION = "${base}.${env.BUILD_NUMBER}-${sha}"
                    currentBuild.displayName = "#${env.BUILD_NUMBER} v${env.APP_VERSION}"
                }
                echo "This build will be version ${env.APP_VERSION}"
            }
        }

        // ---------- 2. TEST: compile + unit & API tests ----------
        stage('Unit Tests') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                sh "mvn -B -Drevision=${env.APP_VERSION} clean test"
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        // ---------- 3. QUALITY GATE: fail if coverage is too low ----------
        stage('Code Coverage Gate') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                sh 'mvn -B jacoco:check@coverage-check'
            }
            post {
                always {
                    recordCoverage(tools: [[parser: 'JACOCO', pattern: 'target/site/jacoco/jacoco.xml']])
                }
            }
        }

        // ---------- 4. PACKAGE: versioned jar + versioned Docker image ----------
        stage('Package') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                sh "mvn -B -Drevision=${env.APP_VERSION} package -DskipTests"
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                sh "docker build -t ${IMAGE}:${env.APP_VERSION} -t ${IMAGE}:latest ."
            }
        }

        // ---------- 5. DEPLOY TO STAGING + automatic smoke test ----------
        stage('Deploy to Staging') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                deployTo('staging', 8081, env.APP_VERSION)
                smokeTest('staging', env.APP_VERSION)
            }
        }

        // ---------- 6. HUMAN APPROVAL GATE ----------
        stage('Approval') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    input message: "Staging looks good (http://localhost:8081). Deploy v${env.APP_VERSION} to PRODUCTION?",
                          ok: 'Deploy to Production'
                }
            }
        }

        // ---------- 7. DEPLOY TO PRODUCTION ----------
        stage('Deploy to Production') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                deployTo('production', 8082, env.APP_VERSION)
                smokeTest('production', env.APP_VERSION)
                script { currentBuild.description = "In production: v${env.APP_VERSION}" }
            }
        }

        // ---------- ROLLBACK: redeploy an image we already built and tested ----------
        stage('Rollback Production') {
            when { expression { params.ACTION == 'rollback' } }
            steps {
                script {
                    def version = params.ROLLBACK_VERSION.trim()
                    if (!version) {
                        error 'ROLLBACK_VERSION is required for a rollback'
                    }
                    sh "docker image inspect ${IMAGE}:${version} > /dev/null"
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ROLLBACK to v${version}"
                    deployTo('production', 8082, version)
                    smokeTest('production', version)
                    currentBuild.description = "In production: v${version} (rollback)"
                }
            }
        }
    }

    post {
        success {
            echo 'Pipeline finished successfully.'
        }
        failure {
            echo 'Pipeline FAILED - nothing after the failed stage was run, production is untouched.'
        }
    }
}

// Replace the running container for an environment with the given image version
def deployTo(String envName, int port, String version) {
    echo "Deploying v${version} to ${envName} (http://localhost:${port})"
    sh """
        docker network inspect ${env.DOCKER_NETWORK} > /dev/null 2>&1 || docker network create ${env.DOCKER_NETWORK}
        docker rm -f inventory-${envName} > /dev/null 2>&1 || true
        docker run -d --name inventory-${envName} \
            --network ${env.DOCKER_NETWORK} \
            -p ${port}:8080 \
            -e APP_ENV=${envName} \
            --restart unless-stopped \
            ${env.IMAGE}:${version}
    """
}

// Wait until the app is healthy and confirm the expected version is running
def smokeTest(String envName, String version) {
    def url = "http://inventory-${envName}:8080"
    retry(20) {
        sleep 3
        sh "curl -fsS ${url}/actuator/health | grep -q UP"
    }
    sh "curl -fsS ${url}/version"
    sh "curl -fsS ${url}/version | grep -q '${version}'"
    echo "Smoke test passed: ${envName} is running v${version}"
}
