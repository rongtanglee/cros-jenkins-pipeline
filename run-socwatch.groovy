def product_name
def current_dt
def kernel_release


pipeline {
    agent {
        label 'jenkins-master'
    }
    
    parameters {
        string defaultValue: '192.168.1.102', description: 'DUT IP address', name: 'dut_ip'
        string defaultValue: '-m -z -s 0 -t 30 -f sys -f device -f xhci -f pcie  -f chipset-all -f pch-slps0-dbg -f s0ix-subs-res -f s0ix-subs-status -f gfx -f panel-srr -f pkg-pwr -f cpu-pkgc-dbg -f pch-slps0 -f pch-ip-active -f cpu -f cpu-cstate -f cpu-pstate', description: 'socwatch arguments', name: 'socwatch_args', trim: true
    
        booleanParam defaultValue: false, description: 'Install Socwatch', name: 'install_socwatch'
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
                    
                    Date date = new Date()
                    current_dt = date.format('yyyyMMdd-HHmmss')
                    echo "current_dt: ${current_dt}"
                    
                    kernel_release = sh(script: "ssh root@${params.dut_ip} 'uname -r'", returnStdout: true).trim()
                    echo "kernel_release: ${kernel_release}"
                }
            }
        }
        
        stage('Install socwatch') {
            when {
                expression { return params.install_socwatch }
            }
            steps {
                build job: 'install-socwatch', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}")]            
            }
        }
        
        stage('Run socwatch') {
            steps {
                script {
                    sh """
                        ssh root@${params.dut_ip} << EOF
                        lsmod | grep socwatch
                        cd /usr/local/socwatch_chrome_CUSTOM/
                        ./socwatch --help
                        ./socwatch ${params.socwatch_args} -o results/socwatch-${product_name}-${current_dt}
                        << EOF
                    """
                }
            }
            
            post {
                success {
                    sh "scp -r root@${params.dut_ip}:/usr/local/socwatch_chrome_CUSTOM/results/* ."
                    archiveArtifacts "socwatch-${product_name}-${current_dt}.*"
                }
            }
        }

    }
}
