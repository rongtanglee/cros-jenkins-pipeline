def log_file = ''

pipeline {
    agent {
        label 'jenkins-master'
    }
    
    parameters {
        string defaultValue: '192.168.1.102', description: 'DUT IP address', name: 'dut_ip'
        string defaultValue: '10000', description: 'Number of iterations', name: 'count'
        string defaultValue: '10', description: 'Max seconds to suspend', name: 'suspend_max'
        string defaultValue: '5', description: 'Min seconds to suspend', name: 'suspend_min'
        string defaultValue: '10', description: 'Max seconds to stay awake', name: 'wake_max'
        string defaultValue: '5', description: 'Min seconds to stay awake', name: 'wake_min'
        
        string defaultValue: '', description: 'Pre-suspend command', name: 'pre_suspend_command'
        string defaultValue: '', description: 'Post-resume command', name: 'post_resume_command'
        string defaultValue: '', description: 'Record dmesg directory', name: 'record_dmesg_dir'
        
        booleanParam defaultValue: true, description: 'Abort on any premature wakes from suspend', name: 'premature_wake_fatal'
        booleanParam defaultValue: true, description: 'Abort on any late wakes from suspend', name: 'late_wake_fatal'
        booleanParam defaultValue: false, description: 'Ignore Intel s0ix substates and only check generic s0ix counter', name: 'ignore_s0ix_substates'
        
        booleanParam defaultValue: false, description: 'Collect logs', name: 'collect_logs'
        
    }

    stages {
        stage('Check DUT') {
            steps {
                sh 'printenv'
                echo "Check DUT IP: ${params.dut_ip}"
                sh "ssh root@${params.dut_ip} 'crossystem'"
                
                script {
                    def product_name = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s baseboard-product-name'", returnStdout: true).trim().toLowerCase()
                    def (_m, _f) = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s system-family'", returnStdout: true).trim().toLowerCase().split('_')
                    def family_name = _f
                    echo "product_name: ${product_name}"
                    echo "family_name: ${family_name}"
                    
                    env.DUT_IP = "${params.dut_ip}"
                    env.DUT_BOARD = "${family_name}"
                    env.DUT_NAME = "${product_name}"
                    
                    sh 'printenv'
                }
            }
        }
        
        stage('Run suspend_stress_test') {
            steps {
                script {
                //sh "ssh root@${params.dut_ip} 'suspend_stress_test --count ${params.count} --suspend_max ${params.suspend_max} --suspend_min ${params.suspend_min} --wake_max ${params.wake_max} --wake_min ${params.wake_min} --premature_wake_fatal ${params.premature_wake_fatal} --late_wake_fatal ${params.late_wake_fatal}'"
                    def cmd_str = "suspend_stress_test --count ${params.count} --suspend_max ${params.suspend_max} --suspend_min ${params.suspend_min} --wake_max ${params.wake_max} --wake_min ${params.wake_min} --premature_wake_fatal ${params.premature_wake_fatal} --late_wake_fatal ${params.late_wake_fatal}"
                    if (!params.pre_suspend_command.isEmpty()) {
                        cmd_str = cmd_str + " --pre_suspend_command " + "\'\\\'" + "${params.pre_suspend_command}" + "\\\'\'"
                    }
                    if (!params.post_resume_command.isEmpty()) {
                        cmd_str = cmd_str + " --post_resume_command " + "\'\\\'" + "${params.post_resume_command}" + "\\\'\'"
                    }
                    if (!params.record_dmesg_dir.isEmpty()) {
                        cmd_str = cmd_str + " --record_dmesg_dir " + "\'\\\'" + "${params.record_dmesg_dir}" + "\\\'\'"
                    }
                    println cmd_str
                    
                    def BuildJob = build job: 'run-command-on-dut', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'dut_cmd', value: "${cmd_str}")]
                    println(BuildJob.getBuildVariables()["BUILD_RESULT"])
                    
                    BuildLog = Jenkins.getInstance().getItemByFullName('run-command-on-dut').getBuildByNumber(BuildJob.getNumber()).log
                    println(BuildLog)
                }
            }
        }
        
        stage('Collect logs') {
            when {
                expression { params.collect_logs == true }
            }
            steps {
                echo 'Collect logs'
                
                script {
                    log_file = sh(returnStdout: true, script: "ssh root@${params.dut_ip} 'generate_logs 2>&1 > /dev/null | grep -oE \'/tmp/debug-logs_[0-9]{8}-[0-9]{6}\\.tgz\' | sed \'s#.*/##\''").trim()
                    println log_file
                    
                    dir("${env.WORKSPACE}") {
                        sh "scp root@${params.dut_ip}:/tmp/${log_file} ."
                    }
                }
            }
            
            post {
                success {
                    archiveArtifacts "${log_file}"
                }
            }
        }
        
        stage('Clean logs') {
            when {
                expression { params.collect_logs == true }
            }
            steps {
                sh "ssh root@${params.dut_ip} 'cd /tmp && rm -f debug-logs_*'"
            }
        }
    }
    
}
