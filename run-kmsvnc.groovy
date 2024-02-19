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
        
        stage('Run kmsvnc') {
            steps {
                script {
                    sh """
                        ssh root@${params.dut_ip} << EOF
                        iptables -A INPUT -m state --state NEW -p tcp --dport 6080 -j ACCEPT
                        iptables -A INPUT -m state --state NEW -p tcp --dport 5900 -j ACCEPT
                        nohup kmsvnc >> /dev/null 2>&1 &
                        nohup novnc >> /dev/null 2>&1 &
                        << EOF
                    """
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
