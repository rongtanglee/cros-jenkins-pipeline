def product_name

pipeline {
    agent {
        label 'jenkins-master'
    }
    
    parameters {
        string defaultValue: '192.168.1.102', description: 'DUT IP address', name: 'dut_ip'
    }

    stages {
        stage('Check DUT') {
            steps {
                sh 'printenv'
                echo "Check DUT IP: ${params.dut_ip}"
                sh "ssh root@${params.dut_ip} 'crossystem'"
                script {
                    product_name = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s baseboard-product-name'", returnStdout: true).trim()
                    echo "product_name: ${product_name}"
                }
            }
        }
        
        stage('Remove rootfs verification') {
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') {
                    build job: 'remove-rootfs-verification', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}")]
                }
            }
        }
        
        stage('Install kmsvnc') {
            steps {
                dir("${env.HOME}/tools/kmsvnc") {
                    sh "ssh root@${params.dut_ip} 'mount -o remount,rw /'"
                    sh "scp vnc.conf root@${params.dut_ip}:/etc/init/"
                }
            }
        }
        
        stage('Reboot DUT') {
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') {
                    timeout(time: 15, unit: "SECONDS") {
                        sh "ssh root@${params.dut_ip} 'reboot'"
                    }
                    sleep(time:30, unit:"SECONDS")
                }
                
                // Wait the DUT rebooting
                retry(20) {
                    timeout(time: 10, unit: "SECONDS") {
                        sh "ping -c 3 ${params.dut_ip}"
                    }
                }
            }
        }
    }
    
    post {
        always {
            buildDescription "Click <a href=\"http://${params.dut_ip}:6080/vnc.html?host=${params.dut_ip}&port=6080\" target=\"_blank\">this link</a> to open ${product_name} DUT's NoVNC connection"
        }
    }
}
