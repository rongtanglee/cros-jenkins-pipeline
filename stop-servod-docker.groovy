pipeline {
    agent {
        label 'jenkins-master'
    }
    
    environment {
        PATH="${env.HOME}/cros-tot/src/third_party/hdctools/scripts:${env.PATH}"
    }
    
    parameters {
        string defaultValue: '', description: 'Container Name, [container_name]-docker_servod', name: 'container_name'
    }

    stages {
        stage('Stop servod docker') {
            steps {
                script {
                    def status = sh returnStatus: true, script: 'servod-ps'
                    if (status != 0) {
                        echo "servod container is not running"
                    } else {
                        def stop_servod_args = ''
                        if (params.container_name) {
                            stop_servod_args = "-n ${params.container_name}"
                        }
                        echo "servod container is running, stop it"
                        sh "stop-servod ${stop_servod_args}"
                    }
                }
            }
        }
    }
}
