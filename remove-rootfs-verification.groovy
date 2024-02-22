def product_name
roofs_verfication_removed = false

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
                script {
                    def cmdline = sh(script: "ssh root@${params.dut_ip} 'cat /proc/cmdline'", returnStdout: true).trim()
                    echo "cmdline: ${cmdline}"
                    
                    if (cmdline.contains('root=/dev/dm-0')) {
                        sh "ssh root@${params.dut_ip} '/usr/share/vboot/bin/make_dev_ssd.sh --remove_rootfs_verification --force'"
                    } else {
                        echo "DUT have already remove rootfs verification"
                        roofs_verfication_removed = true
                    }
                }
                
            }
        }
        
        stage('Reboot DUT') {
            when { 
                expression { return (!roofs_verfication_removed) } 
            }
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') {
                    timeout(time:15, unit: "SECONDS") {
                        sh "ssh root@${params.dut_ip} 'shutdown -r now'"
                    }
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
}
