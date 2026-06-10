// Jenkinsfile — Spring WebFlux microservice
//
// Pipeline:
//   1. Checkout
//   2. Build + test (Maven)
//   3. Static analysis (SpotBugs + Checkstyle)
//   4. Package (fat jar)
//   5. Build & push Docker image
//   6. Deploy to staging (manual gate)

pipeline {
    agent any

    tools {
        jdk 'JDK-21'
        maven 'Maven-3.9'
    }

    environment {
        REGISTRY = "registry.example.com"
        IMAGE_NAME = "spring-webflux-microservice"
        IMAGE_TAG  = "${env.BUILD_NUMBER}"
        JAVA_OPTS  = "-XX:+UseG1GC -XX:MaxGCPauseMillis=200"
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build + Test') {
            steps {
                sh 'mvn -B -ntp clean verify'
            }
            post {
                always {
                    junit 'target/surefire-reports/TEST-*.xml,target/failsafe-reports/TEST-*.xml'
                    jacoco(execPattern: 'target/jacoco.exec', minimumLineCoverage: '0.70')
                }
            }
        }

        stage('Static Analysis') {
            parallel {
                stage('SpotBugs') {
                    steps {
                        sh 'mvn -B -ntp spotbugs:check'
                    }
                }
                stage('Checkstyle') {
                    steps {
                        sh 'mvn -B -ntp checkstyle:check'
                    }
                }
            }
        }

        stage('Package') {
            steps {
                sh 'mvn -B -ntp package -DskipTests'
            }
        }

        stage('Docker Build + Push') {
            steps {
                script {
                    docker.withRegistry("https://${env.REGISTRY}", 'registry-credentials') {
                        def image = docker.build("${env.REGISTRY}/${env.IMAGE_NAME}:${env.IMAGE_TAG}")
                        image.push()
                        image.push('latest')
                    }
                }
            }
        }

        stage('Deploy to Staging') {
            when {
                branch 'main'
            }
            steps {
                sh "kubectl set image deployment/webflux-ms webflux-ms=${env.REGISTRY}/${env.IMAGE_NAME}:${env.IMAGE_TAG} -n staging"
                sh "kubectl rollout status deployment/webflux-ms -n staging"
            }
        }
    }

    post {
        success {
            echo 'Pipeline completed successfully.'
        }
        failure {
            mail to: 'team@example.com',
                 subject: "Build failed: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                 body: "Check ${env.BUILD_URL}"
        }
    }
}
