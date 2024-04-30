def container_name
def container_id
def product_name
def family_name

pipeline {
    agent {
        label 'jenkins-master'
    }
    
    parameters {
        string defaultValue: '192.168.1.115', description: 'DUT IP address', name: 'dut_ip'
        choice choices: ['auto', 'rex', 'brya', 'brask', 'dedede'], description: 'Select Platform Board', name: 'board'
        string defaultValue: 'R123', description: 'OS Image Release/Milestone', name: 'release', trim: true
        string defaultValue: '15786.9.0', description: 'OS Image Version/Prefix', name: 'version', trim: true
        string defaultValue: 'PSSD', description: 'USB Key Model Name ($lsblk -o MODEL)', name: 'usb_key_model', trim: true
    }

    stages {
        stage('Check DUT') {
            steps {
                echo "Check DUT"
                script {
                    product_name = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s baseboard-product-name'", returnStdout: true).trim().toLowerCase()
                    def (_m, _f) = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s system-family'", returnStdout: true).trim().toLowerCase().split('_')
                    family_name = _f
                    echo "product_name: ${product_name}"
                    echo "family_name: ${family_name}"
                    sh 'printenv'
                }
            }
        }

        stage('Start servod docker') {
            steps {
                build job: 'start-servod-docker', parameters: [string(name: 'board', value: "${family_name}"), string(name: 'model', value: "${product_name}"), string(name: 'channel', value: 'release'), string(name: 'serial_number', value: ''), string(name: 'container_name', value: 'chromeos-install'), string(name: 'port', value: '9999'), string(name: 'host_directory', value: ''), string(name: 'container_mount_point', value: '')]
            }
        }
        
        stage('Switch USB key Top to Host') {
            steps {
                echo "Switch USB key Top to Host"
                build job: 'dut-control', parameters: [string(name: 'controls', value: 'usb3_mux_en:on top_usbkey_pwr:on top_usbkey_mux:servo_sees_usbkey')]
                sleep(time: 5, unit:"SECONDS")
            }
        }
        
        stage('Flash OS Image to USB key') {
            steps {
                echo "Flash CrOS image to USB key"
                build job: 'flash-cros-image', parameters: [string(name: 'board', value: "${family_name}"), string(name: 'release', value: "${params.release}"), string(name: 'version', value: "${params.version}"), string(name: 'usb_key_model', value: "${params.usb_key_model}")]
                
            }
        }
        
        stage('Switch USB key Top to DUT') {
            steps {
                echo "Switch USB key Top to Host"
                build job: 'dut-control', parameters: [string(name: 'controls', value: 'usb3_mux_en:on top_usbkey_pwr:on top_usbkey_mux:dut_sees_usbkey')]
                sleep(time: 5, unit:"SECONDS")
            }
        }
        
        stage('DUT Enter Recovery mode') {
            steps {
                echo "Set DUT to Recover mode"
                build job: 'dut-control', parameters: [string(name: 'controls', value: 'power_state:rec')]
                sleep(time: 15, unit:"SECONDS")
            }
        }
        
        stage('ChromeOS Install') {
            steps {
                retry(20) {
                    timeout(time: 10, unit: "SECONDS") {
                        sh "ping -c 3 ${params.dut_ip}"
                    }
                }
                
                echo "chromeos-install"
                sh "ssh root@${params.dut_ip} 'crossystem'"
                sh "ssh root@${params.dut_ip} 'chromeos-install --storage_diags --yes'"
                sh "ssh root@${params.dut_ip} 'sync && reboot'"
            }
        }
    }
    
    post {
        always {
            echo "Stop Servod Docker"
            build job: 'stop-servod-docker', parameters: [string(name: 'container_name', value: 'chromeos-install')]
        }
    }
}
