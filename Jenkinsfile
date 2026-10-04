// Combined CI/CD for post-api-kotlin-muserver (Kotlin mu-server + vanilla TS frontend).
// Pipeline-from-SCM: Jenkins checks out this repo, runs THIS file.
//
// Build images into Jenkins agent's local docker daemon (host docker.sock mounted).
// K8s pulls with imagePullPolicy: Never.
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
        // Hardcode absolute paths so we always know cwd after multi-stage sh blocks.
        WORKSPACE = "${env.WORKSPACE}"
    }
    stages {
        stage('Build backend (gradle, sync, clean)') {
            when { expression { params.MODE == 'ci' || params.MODE == 'both' } }
            steps {
                // `clean` forces a full rebuild — avoids stale UP-TO-DATE results
                // hiding source changes. `--no-daemon` ensures synchronous exit.
                // Single-command sh (no `cd` inside) keeps cwd = $WORKSPACE.
                sh '''
                set -euo pipefail
                cd "$WORKSPACE"
                ~/.local/gradle/gradle-8.10.2/bin/gradle clean build -x test --no-daemon
                '''
            }
        }
        stage('Build frontend (vite)') {
            when { expression { params.MODE == 'ci' || params.MODE == 'both' } }
            steps {
                sh '''
                set -euo pipefail
                cd "$WORKSPACE/frontend"
                npm ci
                npm run build:fast
                cd "$WORKSPACE"
                pwd && ls dist 2>/dev/null || ls frontend/dist
                '''
            }
        }
        stage('Build Docker images (local)') {
            when { expression { params.MODE == 'ci' || params.MODE == 'both' } }
            steps {
                sh '''
                set -euo pipefail
                cd "$WORKSPACE"
                # Verify Dockerfile exists (defensive — was the previous bug)
                test -f Dockerfile || { echo "ERROR: Dockerfile missing in $WORKSPACE"; ls -la; exit 1; }
                test -f frontend/Dockerfile || { echo "ERROR: frontend/Dockerfile missing"; ls -la frontend/; exit 1; }
                docker build -t ${BACKEND_IMAGE}:${IMAGE_TAG} -t ${BACKEND_IMAGE}:latest -f Dockerfile .
                docker build -t ${FRONTEND_IMAGE}:${IMAGE_TAG} -t ${FRONTEND_IMAGE}:latest -f frontend/Dockerfile frontend
                docker images | grep -E "post-api-(backend|frontend)" || true
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
                cd "$WORKSPACE"
                kubectl apply -f k8s/namespace.yaml || true
                # Delete old post-api (Java) deployment if it exists, so our new
                # post-api-backend / post-api-frontend can take over the namespace.
                kubectl -n ${NAMESPACE} delete deployment post-api --ignore-not-found
                kubectl -n ${NAMESPACE} delete service post-api-svc --ignore-not-found
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
                cd "$WORKSPACE/frontend"
                export PLAYWRIGHT_BASE_URL=http://192.168.232.128:30080
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
