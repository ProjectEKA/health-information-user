podTemplate(containers: [
    containerTemplate(
      name: 'aws-k8s-helm3',
      image: 'projecteka/aws-k8s-helm3:latest',
      resourceRequestCpu: '100m',
      resourceLimitCpu: '300m',
      resourceRequestMemory: '300Mi',
      resourceLimitMemory: '500Mi',
      ttyEnabled: true,
      command: 'cat',
    )
    ],
    volumes: [
    hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock')
    ]) {
    node(POD_LABEL) {
        properties([
            parameters([
                choice(choices: ['nha-dev','nha-app', 'nha-cm', 'nha-gw', 'nha-app-demo1'], description: 'Select namespace to be used', name: 'namespace'),
                choice(choices: ['aws-dev','dev', 'uat'], description: 'Select environment name', name: 'environment'),
                string(defaultValue: 'latest', description: 'Image tag to be used', name: 'image_tag', trim: true)
            ])
        ])

        // ToDo: pull validations to a shared function
        if ( params.image_tag == '' ) {
             currentBuild.result = 'ABORTED'
             error("Image tag parameter is mandatory")
        }

        if ( params.namespace == '' || params.namespace == 'null') {
            currentBuild.result = 'ABORTED'
            error("Namespace parameter is mandatory")
        }

        if ( params.environment == '' || params.environment == 'null' ) {
             currentBuild.result = 'ABORTED'
             error("Environment parameter is mandatory")
        }

        def IMAGE_TAG = "${params.image_tag}"
        def NAMESPACE = "${params.namespace}"
        def KUBE_CONFIG_ID = (params.environment == 'dev') ? 'sandbox_k8s_config':(params.environment == 'uat') ? 'uat_k8s_config': 'aws_dev_kubeconfig_id'
        def VALUES_YAML = (params.environment == 'dev') ? 'values.yaml' : (params.environment == 'uat') ? 'values-uat.yaml':'values-aws-dev.yaml'
        def DB_PASSWORD_CRED_ID = (params.environment == 'dev') ? 'DB_PASSWORD_DEV' : (params.environment == 'uat') ?  'DB_PASSWORD_UAT' :  'DB_PASSWORD_AWS_DEV'
        def ORTHANC_PASSWORD_CRED_ID = (params.environment == 'dev') ? 'ORTHANC_PASSWORD_DEV' : (params.environment == 'uat') ? 'ORTHANC_PASSWORD_UAT' : 'ORTHANC_PASSWORD_AWS_DEV'
        def REDIS_PASSWORD_CRED_ID = (params.environment == 'dev') ? 'REDIS_PASSWORD_DEV' : (params.environment == 'uat') ? 'REDIS_PASSWORD_UAT' : 'REDIS_PASSWORD_AWS_DEV'
        def NDHM_DOCKER_HUB_PASSWORD_CRED_ID = (params.environment == 'dev') ? 'NDHM_DOCKER_HUB_PASSWORD_DEV' : 'NDHM_DOCKER_HUB_PASSWORD_UAT'
        def HIU_SECRET_CRED_ID = (params.environment == 'dev') ? 'HIU_SECRET_DEV' :  (params.environment == 'uat') ? 'HIU_SECRET_UAT' : 'HIU_SECRET_AWS_DEV'
        def HAS_CLIENT_SECRET_CRED_ID = (params.environment == 'dev') ? 'HAS_CLIENT_SECRET_DEV' : (params.environment == 'uat') ? 'HAS_CLIENT_SECRET_UAT' : 'HAS_CLIENT_SECRET_AWS_DEV'
        def HIU_CLIENT_SECRET_CRED_ID = (params.environment == 'dev') ? 'HIU_CLIENT_SECRET_DEV' : (params.environment == 'uat') ? 'HIU_CLIENT_SECRET_UAT' : 'HIU_CLIENT_SECRET_AWS_DEV'
        def RABBITMQ_CRED_ID = (params.environment == 'dev') ? 'RABBITMQ_CRED_DEV' :  (params.environment == 'uat') ? 'RABBITMQ_CRED_UAT' : 'RABBITMQ_CRED_AWS_DEV'
        def HELM_APP_NAME = "hiu"
        def HELM_CHART_DIRECTORY = "helm_chart/hiu/helm_chart/hiu"

        stage('Get latest version of code') {
          checkout scm
        }

        stage('Deploy hiu to aws-dev k8s cluster') {
            if(params.environment == 'aws-dev'){
                container('aws-k8s-helm3') {
                    withCredentials([
                        [ $class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws_dev_credentials', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                        string(credentialsId: "${DB_PASSWORD_CRED_ID}", variable: 'DB_PASSWORD'),
                        string(credentialsId: "${NDHM_DOCKER_HUB_PASSWORD_CRED_ID}", variable: 'NDHM_DOCKER_HUB_PASSWORD'),
                        string(credentialsId: "${HIU_CLIENT_SECRET_CRED_ID}", variable: 'HIU_CLIENT_SECRET'),
                        string(credentialsId: "${HIU_SECRET_CRED_ID}", variable: 'HIU_SECRET'),
                        string(credentialsId: "${HAS_CLIENT_SECRET_CRED_ID}", variable: 'HAS_CLIENT_SECRET'),
                        string(credentialsId: "${ORTHANC_PASSWORD_CRED_ID}", variable: 'ORTHANC_PASSWORD'),
                        string(credentialsId: "${REDIS_PASSWORD_CRED_ID}", variable: 'REDIS_PASSWORD'),
                        string(credentialsId: "${RABBITMQ_CRED_ID}",variable: 'RABBITMQ_CRED_PSW')
                    ]) {
                        withKubeConfig([credentialsId: "${KUBE_CONFIG_ID}"]) {
                            sh "helm lint ./${HELM_CHART_DIRECTORY}"
                            sh "kubectl create secret docker-registry ndhm-dockerhub-repo --docker-server=index.docker.io --docker-username=ndhm --docker-password=${NDHM_DOCKER_HUB_PASSWORD} --docker-email=ndhm.fhr.eka@gmailcom -n ${NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -"
                            sh "helm upgrade --install --atomic --cleanup-on-fail -f ./${HELM_CHART_DIRECTORY}/${VALUES_YAML} --namespace ${NAMESPACE} ${HELM_APP_NAME} ./${HELM_CHART_DIRECTORY} --set image.tag='${IMAGE_TAG}' --set-string env.secrets.REDIS_PASSWORD='${REDIS_PASSWORD}'  --set-string env.secrets.POSTGRES_PASSWORD='${DB_PASSWORD}' --set-string env.secrets.ORTHANC_PASSWORD='${ORTHANC_PASSWORD}' --set-string env.secrets.HIU_CLIENT_SECRET='${HIU_CLIENT_SECRET}' --set-string env.secrets.HAS_CLIENT_SECRET='${HAS_CLIENT_SECRET}' --set-string env.secrets.HIU_SECRET='${HIU_SECRET}'  --set-string env.secrets.RABBITMQ_PASSWORD='${RABBITMQ_CRED_PSW}'"
                            sh "kubectl get pods -n ${NAMESPACE}"
                        }
                    }
                }
            }
        }

        stage('Deploy hiu to k8s cluster') {
            if(params.environment == 'dev' || params.environment == 'uat'){
                container('aws-k8s-helm3') {
                    withCredentials([string(credentialsId: "${DB_PASSWORD_CRED_ID}", variable: 'DB_PASSWORD'),
                        string(credentialsId: "${NDHM_DOCKER_HUB_PASSWORD_CRED_ID}", variable: 'NDHM_DOCKER_HUB_PASSWORD'),
                        string(credentialsId: "${HIU_SECRET_CRED_ID}", variable: 'HIU_SECRET'),
                        string(credentialsId: "${HIU_CLIENT_SECRET_CRED_ID}", variable: 'HIU_CLIENT_SECRET'),
                        string(credentialsId: "${ORTHANC_PASSWORD_CRED_ID}", variable: 'ORTHANC_PASSWORD'),
                        string(credentialsId: "${HAS_CLIENT_SECRET_CRED_ID}", variable: 'HAS_CLIENT_SECRET'),
                        string(credentialsId: "${REDIS_PASSWORD_CRED_ID}", variable: 'REDIS_PASSWORD'),
                        usernamePassword(credentialsId: "${RABBITMQ_CRED_ID}",
                                     usernameVariable: 'RABBITMQ_CRED_USR',
                                     passwordVariable: 'RABBITMQ_CRED_PSW')
                    ]) {
                        withKubeConfig([credentialsId: "${KUBE_CONFIG_ID}"]) {
                            sh "helm lint ./${HELM_CHART_DIRECTORY}"
                            sh "kubectl create secret docker-registry ndhm-dockerhub-repo --docker-server=index.docker.io --docker-username=ndhm --docker-password=${NDHM_DOCKER_HUB_PASSWORD} --docker-email=ndhm.fhr.eka@gmailcom -n ${NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -"
                            sh "helm upgrade --install --atomic --cleanup-on-fail -f ./${HELM_CHART_DIRECTORY}/${VALUES_YAML} --namespace ${NAMESPACE} ${HELM_APP_NAME} ./${HELM_CHART_DIRECTORY} --set image.tag='${IMAGE_TAG}' --set-string env.secrets.REDIS_PASSWORD='${REDIS_PASSWORD}' --set-string env.secrets.HIU_SECRET='${HIU_SECRET}' --set-string env.secrets.POSTGRES_PASSWORD='${DB_PASSWORD}' --set-string env.secrets.ORTHANC_PASSWORD='${ORTHANC_PASSWORD}' --set-string env.secrets.HAS_CLIENT_SECRET='${HAS_CLIENT_SECRET}' --set-string env.secrets.HIU_CLIENT_SECRET='${HIU_CLIENT_SECRET}' --set-string env.normal.RABBITMQ_USERNAME='${RABBITMQ_CRED_USR}' --set-string env.secrets.RABBITMQ_PASSWORD='${RABBITMQ_CRED_PSW}'"
                            sh "kubectl get pods -n ${NAMESPACE}"
                        }
                    }
                }
            }
        }
    }
}