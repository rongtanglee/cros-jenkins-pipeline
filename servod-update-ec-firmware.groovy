pipeline {
    agent {
        label 'jenkins-master'
    }
    
    parameters {
        choice choices: ['brya', 'brask', 'rex', 'brox', 'nissa', 'dedede'], description: 'Choose Reference Board', name: 'board'
        string defaultValue: '', description: 'Model Name, ex: yaviks, omnigul, karis', name: 'model'

        stashedFile description: 'Upload EC FW Image File', name: 'image_file'
    }

    stages {
        stage('Upload FW image') {
            steps {
                cleanWs()
                script{
                    sh 'printenv'
                    unstash 'image_file'
                    sh "mv image_file ${env.image_file_FILENAME}"
                }
            }
        }
        
        stage('Start servod-docker') {
            steps {
                build job: 'start-servod-docker', parameters: [string(name: 'board', value: "${params.board}"), string(name: 'model', value: "${params.model}"), string(name: 'channel', value: 'release'), string(name: 'serial_number', value: ''), string(name: 'container_name', value: 'update-ec-firmware'), string(name: 'port', value: '9999'), string(name: 'host_directory', value: "${env.WORKSPACE}"), string(name: 'container_mount_point', value: '/workspace')]
            }
        }
        
        stage('Update FW Image') {
            steps {
                sh """
                    docker exec update-ec-firmware-docker_servod ls -la /workspace
                    docker exec update-ec-firmware-docker_servod flash_ec --board ${params.board} --nouse_i2c_pseudo --verbose --image /workspace/${env.image_file_FILENAME}
                """
            }
        }
        
        
    }

    post {
        always {
            echo "Stop Servod Docker"
            build job: 'stop-servod-docker', parameters: [string(name: 'container_name', value: 'update-ec-firmware')]
        }
    }

}
