// ToDo: See if extracting shared lib helps avoiding duplicate jenkins files for prod and non-prod
podTemplate(containers: [
    containerTemplate(
      name: 'helm3',
      image: 'harbor.nhadclmgm.tatacommunications.com/jenkins/helm:latest',
      resourceRequestCpu: '100m',
      resourceLimitCpu: '300m',
      resourceRequestMemory: '300Mi',
      resourceLimitMemory: '500Mi',
      ttyEnabled: true,
      command: 'cat',
    )
  ],
  ) {
    node(POD_LABEL) {
        properties([
            parameters([
                choice(choices: ['nha-cm', 'nha-gw'], description: 'Select namespace to be used', name: 'namespace'),
                choice(choices: ['prod'], description: 'Select environment name', name: 'environment'),
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
        def KUBE_CONFIG_ID = 'prod_k8s_config'
        def VALUES_YAML = 'values-prod.yaml'
        def REDIS_PASSWORD_CRED_ID = 'REDIS_PASSWORD_PROD'
        def DB_PASSWORD_CRED_ID = 'DB_PASSWORD_PROD'
        def ORTHANC_PASSWORD_CRED_ID = 'ORTHANC_PASSWORD_PROD'
        def PATIENT_HIU_CLIENT_SECRET_CRED_ID = 'PATIENT_HIU_CLIENT_SECRET_PROD'
        def HAS_CLIENT_SECRET_PHIU_CRED_ID = 'HAS_CLIENT_SECRET_PHIU_PROD'
        def NDHM_DOCKER_HUB_PASSWORD_CRED_ID = 'NDHM_DOCKER_HUB_PASSWORD_PROD'
        def RABBITMQ_CRED_ID = 'RABBITMQ_CRED_PROD'
        def HELM_APP_NAME = "patient-hiu"
        def HELM_CHART_DIRECTORY = "helm_chart/patient-hiu/helm_chart/patient-hiu"

        stage('Get latest version of code') {
          checkout scm
        }
        stage('Deploy patient-hiu to k8s cluster') {
            container('helm3') {
                withCredentials([string(credentialsId: "${DB_PASSWORD_CRED_ID}", variable: 'DB_PASSWORD'),
                    string(credentialsId: "${NDHM_DOCKER_HUB_PASSWORD_CRED_ID}", variable: 'NDHM_DOCKER_HUB_PASSWORD'),
                    string(credentialsId: "${PATIENT_HIU_CLIENT_SECRET_CRED_ID}", variable: 'PATIENT_HIU_CLIENT_SECRET'),
                    string(credentialsId: "${HAS_CLIENT_SECRET_PHIU_CRED_ID}", variable: 'HAS_CLIENT_SECRET_PHIU'),
                    string(credentialsId: "${REDIS_PASSWORD_CRED_ID}", variable: 'REDIS_PASSWORD'),
                    string(credentialsId: "${ORTHANC_PASSWORD_CRED_ID}", variable: 'ORTHANC_PASSWORD'),
                    usernamePassword(credentialsId: "${RABBITMQ_CRED_ID}",
                                 usernameVariable: 'RABBITMQ_CRED_USR',
                                 passwordVariable: 'RABBITMQ_CRED_PSW')
                ]) {
                    withKubeConfig([credentialsId: "${KUBE_CONFIG_ID}"]) {
                        sh "helm lint ./${HELM_CHART_DIRECTORY}"
                        sh "kubectl create secret docker-registry ndhm-dockerhub-repo --docker-server=index.docker.io --docker-username=ndhm --docker-password=${NDHM_DOCKER_HUB_PASSWORD} --docker-email=ndhm.fhr.eka@gmailcom -n ${NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -"
                        sh "helm upgrade --install --atomic --cleanup-on-fail -f ./${HELM_CHART_DIRECTORY}/${VALUES_YAML} --namespace ${NAMESPACE} ${HELM_APP_NAME} ./${HELM_CHART_DIRECTORY} --set image.tag='${IMAGE_TAG}' --set-string env.secrets.REDIS_PASSWORD='${REDIS_PASSWORD}' --set-string env.secrets.POSTGRES_PASSWORD='${DB_PASSWORD}' --set-string env.secrets.ORTHANC_PASSWORD='${ORTHANC_PASSWORD}' --set-string env.secrets.HIU_CLIENT_SECRET='${PATIENT_HIU_CLIENT_SECRET}' --set-string env.normal.RABBITMQ_USERNAME='${RABBITMQ_CRED_USR}' --set-string env.secrets.HAS_CLIENT_SECRET='${HAS_CLIENT_SECRET_PHIU}' --set-string env.secrets.RABBITMQ_PASSWORD='${RABBITMQ_CRED_PSW}'"
                        sh "kubectl get pods -n ${NAMESPACE}"
                    }
                }
            }
        }
    }
}