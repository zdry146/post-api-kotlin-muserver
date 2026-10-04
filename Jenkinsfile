// Combined CI/CD for post-api-kotlin-muserver (Kotlin mu-server + vanilla TS frontend).
// Reuses the wrapper-script + shared-pipeline pattern from post-api-cicd:
//   Jenkins job clones this repo → reads THIS Jenkinsfile → evaluates it.
// MODE=ci   → build + test + push 2 images
// MODE=cd   → apply manifests (with placeholder substitution) + wait + E2E
// MODE=both → ci then cd in one run
//
// Credentials used: aliyun-docker-login (image push), db-password (PG).
// Host PG (192.168.232.128:5432/testdb) is cluster-reachable.

pipeline {
    agent any
    options {
        timeout(time: 45, unit: 'MINUTES')
    }
    parameters {
        choice(
            name: 'MODE',
            choices: ['ci', 'cd', 'both'],
            description: 'ci = build+test+push, cd = deploy to k8s, both = ci then cd'
        )
        choice(
            name: 'IMAGE_TAG',
            choices: ['latest', '1.0.0'],
            description: 'Image tag to deploy (cd mode only)'
        )
        string(name: 'NAMESPACE', defaultValue: 'post-api', description: 'K8s namespace')
        string(name: 'DB_HOST', defaultValue: '192.168.232.128', description: 'PostgreSQL host')
        string(name: 'DB_DATABASE', defaultValue: 'testdb', description: 'PostgreSQL database name')
    }
    environment {
        ALIYUN_REGISTRY      = 'crpi-e2h2rfj3kunrwe5n.cn-hangzhou.personal.cr.aliyuncs.com'
        ALIYUN_NAMESPACE     = 'mike-docker-registry'
        BACKEND_IMAGE        = 'post-api-backend'
        FRONTEND_IMAGE       = 'post-api-frontend'
        FULL_BACKEND_IMAGE   = "${env.ALIYUN_REGISTRY}/${env.ALIYUN_NAMESPACE}/${env.BACKEND_IMAGE}"
        FULL_FRONTEND_IMAGE  = "${env.ALIYUN_REGISTRY}/${env.ALIYUN_NAMESPACE}/${env.FRONTEND_IMAGE}"
        ALIYUN_DOCKER_CREDS  = credentials('aliyun-docker-login')
        FRONTEND_GIT_CREDS   = credentials('git-cred')
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
        stage('Build & push images') {
            when { expression { params.MODE == 'ci' || params.MODE == 'both' } }
            steps {
                sh '''
                set -euo pipefail
                echo "$ALIYUN_DOCKER_CREDS_PSW" | docker login -u "$ALIYUN_DOCKER_CREDS_USR" --password-stdin "$ALIYUN_REGISTRY"
                # Backend (root context, multi-stage gradle→temurin)
                docker build -t "$FULL_BACKEND_IMAGE:$IMAGE_VERSION" -t "$FULL_BACKEND_IMAGE:latest" .
                docker push "$FULL_BACKEND_IMAGE:$IMAGE_VERSION"
                docker push "$FULL_BACKEND_IMAGE:latest"
                # Frontend (./frontend context, node:20-alpine)
                docker build -t "$FULL_FRONTEND_IMAGE:$IMAGE_VERSION" -t "$FULL_FRONTEND_IMAGE:latest" ./frontend
                docker push "$FULL_FRONTEND_IMAGE:$IMAGE_VERSION"
                docker push "$FULL_FRONTEND_IMAGE:latest"
                '''
            }
        }
        stage('Resolve deploy tag') {
            when { expression { params.MODE == 'cd' || params.MODE == 'both' } }
            steps {
                script {
                    env.DEPLOY_TAG = (params.MODE == 'both') ? env.IMAGE_VERSION : params.IMAGE_TAG
                    echo "Deploying tag: ${env.DEPLOY_TAG} (MODE=${params.MODE})"
                }
            }
        }
        stage('Apply manifests') {
            when { expression { params.MODE == 'cd' || params.MODE == 'both' } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'aliyun-docker-login', usernameVariable: 'ALIYUN_USR', passwordVariable: 'ALIYUN_PSW')]) {
                    sh '''
                    set -euo pipefail
                    # Ensure namespace + image-pull secret exist
                    kubectl apply -f k8s/namespace.yaml
                    kubectl -n ${NAMESPACE} create secret docker-registry aliyun-registry-cred \\
                        --docker-server=${ALIYUN_REGISTRY} \\
                        --docker-username=$ALIYUN_USR \\
                        --docker-password=$ALIYUN_PSW \\
                        --dry-run=client -o yaml | kubectl apply -f -
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
                # Frontend is exposed via NodePort 30080 on the host
                export PLAYWRIGHT_BASE_URL=http://192.168.232.128:30080
                npm ci
                export PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
                export PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/snap/bin/chromium
                # Clean DB so tests start from empty (new schema on backend boot)
                curl -sf -X DELETE 'http://192.168.232.128:30080/api/posts/1'  || true
                curl -sf -X DELETE 'http://192.168.232.128:30080/api/posts/2'  || true
                curl -sf -X DELETE 'http://192.168.232.128:30080/api/posts/3'  || true
                npx playwright test --reporter=list
                '''
            }
        }
    }
    post {
        success { echo "Pipeline (MODE=${params.MODE}) succeeded" }
        failure {
            echo "Pipeline (MODE=${params.MODE}) failed"
            sh "kubectl -n ${NAMESPACE} get all || true"  || true
        }
    }
}
