def container_name
def container_id

pipeline {
    agent {
        label 'jenkins-master'
    }
    
    environment {
        PATH="${env.HOME}/cros-tot/src/third_party/hdctools/scripts:${env.PATH}"
    }
    
    parameters {
        //string defaultValue: '192.168.1.102', description: 'DUT IP address', name: 'dut_ip'
        
        choice choices: ['brya', 'brask', 'rex', 'brox', 'nissa', 'dedede'], description: 'Choose Reference Board', name: 'board'
        string defaultValue: '', description: 'Model Name, ex: yaviks, omnigul, karis', name: 'model'
        choice choices: ['release', 'latest', 'beta', 'local'], description: '''local, image built on this machine.
latest, a close to ToT build, may have bugs
beta, used for short period of time to test next release
release, latest release version, typically 2-4 weeks behind,''', name: 'channel'
        string defaultValue: '', description: 'Servo Serial Number, ex: SERVOV4P1-C-2306150024', name: 'serial_number'
        string defaultValue: '', description: 'Container Name', name: 'container_name'
        string defaultValue: '', description: 'Listening Port (default: 9999)', name: 'port'
        string defaultValue: '', description: 'Host Directory to be mount by servod container', name: 'host_directory'
        string defaultValue: '', description: "Container Mount Point, format: host_directory:container_mount_point", name: 'container_mount_point'

    }

    stages {
        stage('Check Servo Device') {
            steps {
                sh 'lsusb'
                sh 'printenv'
                script {
                    dir("${env.HOME}/tools/scripts") {
                        def status = sh returnStatus: true, script: './check-usb-device.sh 18d1:520d'
                        if (status == 0) {
                            echo "Servo is connected"
                        } else {
                            echo "Servo is not connected"
                            currentBuild.result = 'FAILURE'
                        }
                        
                        status = sh returnStatus: true, script: './check-usb-device.sh 18d1:504a'
                        if (status == 0) {
                            echo "GSC is connected"
                        } else {
                            echo "GSC is not connected"
                            currentBuild.result = 'FAILURE'
                        }
                    }
                    
                }
                
            }
        }
        
        stage('Start servod') {
            steps {
                script {
                    
                    def status = sh returnStatus: true, script: 'servod-ps'
                    if (status == 0) {
                        echo "servod container is already running, stop it"
                        sh 'stop-servod'
                    }
                    
                    def servod_cmd = "start-servod -c ${params.channel} -b ${params.board}"
                    if (!params.model.isEmpty()) {
                        servod_cmd = "${servod_cmd} -m ${params.model}"
                    }
                    if (!params.serial_number.isEmpty()) {
                        servod_cmd = "${servod_cmd} -s ${params.serial_number}"
                    }
                    if (!params.port.isEmpty()) {
                        servod_cmd = "${servod_cmd} -p ${params.port}"
                    }
                    if (!params.container_name.isEmpty()) {
                        servod_cmd = "${servod_cmd} -n ${params.container_name}"
                    }
                    if (!params.host_directory.isEmpty() && !params.container_mount_point.isEmpty()) {
                        servod_cmd = "${servod_cmd} --mount ${params.host_directory}:${params.container_mount_point}"
                    }
                    echo "servod_cmd = ${servod_cmd}"
                    sh "${servod_cmd}"
                    
                    sh "dut-control -- servo_type"
                    sh "servod-ps"
                }
                
            }
        }
        
    }
}
