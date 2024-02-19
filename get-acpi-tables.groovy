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
                sh "ssh root@${params.dut_ip} -t 'crossystem'"
                script {
                    product_name = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s baseboard-product-name'", returnStdout: true).trim()
                    echo "product_name: ${product_name}"
                }
            }
        }
        
        stage('Dump ACPI DSDT/SSDT') {
            steps {
                sh "ssh root@${params.dut_ip} 'mkdir -p /tmp/${product_name}-acpi-tables'"
                sh "ssh root@${params.dut_ip} 'acpidump > /tmp/${product_name}-acpi-tables/acpidata.dat'"
                sh "ssh root@${params.dut_ip} 'cd /tmp/${product_name}-acpi-tables && acpixtract -a /tmp/${product_name}-acpi-tables/acpidata.dat'"
                sh "ssh root@${params.dut_ip} 'cd /tmp/${product_name}-acpi-tables && iasl -d ssdt.dat mcfg.dat apic.dat tpm2.dat dsdt.dat lpit.dat dbg2.dat dmar.dat facp.dat hpet.dat facs.dat'"
            }
        }
        
        stage('Get ACPI tables') {
            steps {
                echo 'Get ACPI tables'
                cleanWs()
                dir ("${product_name}-acpi-tables") {
                    sh "scp root@${params.dut_ip}:/tmp/${product_name}-acpi-tables/*.dsl ."
                }
            }
            
            post {
                always {
                    sh "ssh root@${params.dut_ip} 'rm -rf /tmp/${product_name}-acpi-tables'"
                }
                success {
                    archiveArtifacts "${product_name}-acpi-tables/*.dsl"
                }
            }
        }
    }
    
}
