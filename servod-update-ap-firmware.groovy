pipeline {
    agent {
        label 'jenkins-master'
    }
    
    parameters {
        choice choices: ['brya', 'brask', 'rex', 'brox', 'nissa', 'dedede'], description: 'Choose Reference Board', name: 'board'
        string defaultValue: '', description: 'Model Name, ex: yaviks, omnigul, karis', name: 'model'
        
        booleanParam defaultValue: false, description: 'Add --quirks=no_check_platform', name: 'no_check_platform'

        stashedFile description: 'Upload AP FW Image File', name: 'image_file'
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
                build job: 'start-servod-docker', parameters: [string(name: 'board', value: "${params.board}"), string(name: 'model', value: "${params.model}"), string(name: 'channel', value: 'release'), string(name: 'serial_number', value: ''), string(name: 'container_name', value: 'update-ap-firmware'), string(name: 'port', value: '9999'), string(name: 'host_directory', value: "${env.WORKSPACE}"), string(name: 'container_mount_point', value: '/workspace')]
            }
        }
        
        stage('Update FW Image') {
            steps {
                script {
                    if (params.no_check_platform) {
                        sh """
                            docker exec update-ap-firmware-docker_servod ls -la /workspace
                            docker exec update-ap-firmware-docker_servod futility update --servo -i /workspace/${env.image_file_FILENAME} --quirks=no_check_platform
                        """
                    } else {
                        sh """
                            docker exec update-ap-firmware-docker_servod ls -la /workspace
                            docker exec update-ap-firmware-docker_servod futility update --servo -i /workspace/${env.image_file_FILENAME}
                        """
                    }
                }
                
            }
        }
        
        
    }

    post {
        always {
            echo "Stop Servod Docker"
            build job: 'stop-servod-docker', parameters: [string(name: 'container_name', value: 'update-ap-firmware')]
        }
    }

}
