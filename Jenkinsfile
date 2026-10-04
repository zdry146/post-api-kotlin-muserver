// Combined CI/CD for post-api-kotlin-muserver (Kotlin mu-server + vanilla TS frontend).
// Pipeline-from-SCM (Jenkins built-in): Jenkins checks out this repo, runs THIS file.
//
// Strategy: build images into Jenkins agent's Docker daemon (which has
// /var/run/docker.sock from host) → K8s pulls with imagePullPolicy: Never.
// Skips aliyun registry push (TLS handshake issue in this env).
//
// MODE=ci   → build backend/frontend + tag images locally
// MODE=cd   → apply manifests + wait + E2E
// MODE=both → ci then cd in one run

pipeline {
    agent any
    options {
        timeout(time: 45, unit: 'MINUTES')
    }
    parameters {
        choice(name: 'MODE', choices: ['ci', 'cd', 'both'], description: 'ci / cd / both')
        choice(name: 'IMAGE_TAG', choices: ['latest', '1.0.0'], description: 'Image tag (cd mode)')
        string(name: 'NAMESPACE', defaultValue: 'post-api', description: 'K8s namespace')
        string(name: 'DB_HOST', defaultValue: '192.168.232.128', description: 'PostgreSQL host')
        string(name: 'DB_DATABASE', defaultValue: 'testdb', description: 'PostgreSQL database name')
    }
    environment {
        BACKEND_IMAGE  = 'post-api-backend'
        FRONTEND_IMAGE = 'post-api-frontend'
    }
    stages {
        stage('Build backend (gradle)') {
            when { expression { params.MODE == 'ci' || params.MODE == 'both' } }
            steps {
                sh '''
                set -euo pipefail
                ~/.local/gradle/gradle-8.10.2/bin/gradle build -x test --no-daemon
                '''
            }
        }
        stage('Build frontend (vite)') {
            when { expression { params.MODE == 'ci' || params.MODE == 'both' } }
            steps {
                sh '''
                set -euo pipefail
                cd frontend
                npm ci
                npm run build:fast
                cd ..
                '''
            }
        }
        stage('Build Docker images (local)') {
            when { expression { params.MODE == 'ci' || params.MODE == 'both' } }
            steps {
                sh '''
                set -euo pipefail
                docker build -t ${BACKEND_IMAGE}:${IMAGE_TAG} -t ${BACKEND_IMAGE}:latest .
                docker build -t ${FRONTEND_IMAGE}:${IMAGE_TAG} -t ${FRONTEND_IMAGE}:latest ./frontend
                docker images | grep -E "post-api-(backend|frontend)"
                '''
            }
        }
        stage('Resolve deploy tag') {
            when { expression { params.MODE == 'cd' || params.MODE == 'both' } }
            steps {
                script {
                    env.DEPLOY_TAG = (params.MODE == 'both') ? env.IMAGE_TAG : params.IMAGE_TAG
                    echo "Deploying tag: ${env.DEPLOY_TAG}"
                }
            }
        }
        stage('Apply manifests') {
            when { expression { params.MODE == 'cd' || params.MODE == 'both' } }
            steps {
                sh '''
                set -euo pipefail
                kubectl apply -f k8s/namespace.yaml || true
                # Substitute __IMAGE_TAG__ / __DB_HOST__ / __DB_DATABASE__ placeholders
                for f in k8s/backend.yaml k8s/frontend.yaml; do
                    sed -e "s|__IMAGE_TAG__|${DEPLOY_TAG}|g" \\
                        -e "s|__DB_HOST__|${DB_HOST}|g" \\
                        -e "s|__DB_DATABASE__|${DB_DATABASE}|g" \\
                        "$f" | kubectl -n ${NAMESPACE} apply -f -
                done
                '''
            }
        }
        stage('Wait for ready') {
            when { expression { params.MODE == 'cd' || params.MODE == 'both' } }
            steps {
                sh '''
                set -euo pipefail
                kubectl -n ${NAMESPACE} rollout status deployment/post-api-backend --timeout=180s
                kubectl -n ${NAMESPACE} rollout status deployment/post-api-frontend --timeout=180s
                '''
            }
        }
        stage('E2E: Playwright UI tests') {
            when { expression { params.MODE == 'cd' || params.MODE == 'both' } }
            steps {
                sh '''
                set -euo pipefail
                cd frontend
                # Frontend exposed via NodePort 30080 on the host
                export PLAYWRIGHT_BASE_URL=http://192.168.232.128:30080
                # Skip running curl DELETE cleanup — describe.serial creates fresh posts
                # and TEST_TITLE uses Date.now() so re-runs are isolated.
                export PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
                export PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/snap/bin/chromium
                npx playwright test --reporter=list
                '''
            }
        }
    }
    post {
        success { echo "Pipeline (MODE=${params.MODE}) succeeded" }
        failure {
            echo "Pipeline (MODE=${params.MODE}) failed"
            sh "kubectl -n ${NAMESPACE} get all 2>&1 || true"
        }
    }
}
