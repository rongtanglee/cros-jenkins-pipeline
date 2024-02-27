def product_name
def family_name
def kernel_release
def kernel_version
def commit_id

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
                    
                    def (_m, _f) = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s system-family'", returnStdout: true).trim().toLowerCase().split('_')
                    family_name = _f
                    echo "family_name: ${family_name}"
                    
                    kernel_release = sh(script: "ssh root@${params.dut_ip} 'uname -r'", returnStdout: true).trim()
                    echo "kernel_release: ${kernel_release}"
                    
                    def kernel_parts = kernel_release.split('\\.')
                    kernel_version = 'v' + kernel_parts[0] + '.' + kernel_parts[1]
                    echo "kernel_version: ${kernel_version}"
                    
                    commit_id = kernel_release.replaceAll(/.*g([a-f0-9]+).*/, '$1')
                    echo "commit_id: ${commit_id}"
                }
            }
        }
        
        stage('Build socwatch') {
            steps {
                build job: 'build-socwatch', parameters: [string(name: 'board', value: "${family_name}"), string(name: 'kernel_version', value: "${kernel_version}"), string(name: 'commit_id', value: "${commit_id}")]   
            }
            
            post {
                success {
                    copyArtifacts filter: 'socwatch_chrome_CUSTOM.tar.gz', flatten: true, projectName: 'build-socwatch', selector: workspace(), target: "${env.WORKSPACE}"
                }
            }
        }
        
        stage('Remove rootfs verification') {
            steps {
                build job: 'remove-rootfs-verification', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}")]
            }
        }
        
        stage('Install socwatch to DUT') {
            steps {
                sh "scp socwatch_chrome_CUSTOM.tar.gz root@${params.dut_ip}:/usr/local/"
                script {
                    sh """
                        ssh root@${params.dut_ip} << EOF
                        cd /usr/local/
                        tar xvzf socwatch_chrome_CUSTOM.tar.gz
                        cd socwatch_chrome_CUSTOM
                        mount -o remount,rw /
                        ./install_socwatch_suspend
                        << EOF
                    """
                }
            }
        }

    }
}
