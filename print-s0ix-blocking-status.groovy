def lpm_file = ''
def lpm_file2 = ''
def plat = 'adlp'

pipeline {
    agent {
        label 'jenkins-master'
    }
    
    parameters {
        text description: 'LPM_STATUS_X log message', name: 'lpm_status'
        choice choices: ['ADL-N', 'ADL-P', 'MTL'], description: 'IA SoC platform', name: 'platform'
        booleanParam defaultValue: true, description: 'Compare the LPM_STATUS with other passing values', name: 'diff_lpm_status'
        text description: 'LPM_STATUS_X dump on passing cycle', name: 'lpm_status2'
    }
    
    stages {
        stage('Process LPM_STATUS_X') {
            steps {
                script {
                    echo "LPM_STATUS_X: ${params.lpm_status}"
                    
                    ws {
                        if (params.platform == 'ADL-N') {
                            plat='adln'
                        } else if (params.platform == 'ADL-P') {
                            plat='adlp'
                        } else if (params.platform == 'MTL') {
                            plat='mtl'
                        }
                        
                        def content = params.lpm_status
                        lpm_file = "${env.WORKSPACE}/lpm_status.txt"
                        
                        // Write content to a file in the workspace
                        writeFile file: lpm_file, text: params.lpm_status
                        echo "Content written to ${lpm_file}"
                        
                        if (params.lpm_status2) {
                            echo "Passing LPM_STATUS_X: ${params.lpm_status2}"
                            lpm_file2 = "${env.WORKSPACE}/lpm_status2.txt"
                            writeFile file: lpm_file2, text: params.lpm_status2
                            echo "Content written to ${lpm_file2}"
                        }
                    }
                }
            }
        }
        
        stage('Print s0ix blocking status') {
            steps {
                
                dir("${env.HOME}/tools/s0ix-lpm-status") {
                    ansiColor('xterm') {
                        script {
                            if (params.platform == 'ADL-N' || params.platform == 'ADL-P') {
                                //def lpm_vars = sh returnStdout: true, script: "grep \'LPM_STATUS_[0-5]\' ${lpm_file} | grep -oP \'LPM_STATUS_[0-5]:\\s+\\K.*\' | paste -sd \',\' -"
                                //echo "lpm_vars = ${lpm_vars}"
                                
                                sh """
                                    pwd
                                    . s0ix-venv/bin/activate
                                    python3 adl-s0ix-blocking-status.py ${plat} ${lpm_file}
                                    deactivate
                                """
                            } else if (params.platform == 'MTL') {
                                sh """
                                    pwd
                                    . s0ix-venv/bin/activate
                                    python3 mtl-s0ix-blocking-status.py ${lpm_file}
                                    deactivate
                                """
                            }
                        }
                    }
                }
                
            }
        }
        
        stage('Diff s0ix blocking status') {
            when { 
                expression { return (params.diff_lpm_status) } 
            }
            steps {
                
                dir("${env.HOME}/tools/s0ix-lpm-status") {
                    ansiColor('xterm') {
                        script {
                            if (params.platform == 'ADL-N') {
                                //def lpm_vars = sh returnStdout: true, script: "grep \'LPM_STATUS_[0-5]\' ${lpm_file} | grep -oP \'LPM_STATUS_[0-5]:\\s+\\K.*\' | paste -sd \',\' -"
                                //echo "lpm_vars = ${lpm_vars}"
                                
                                sh """
                                    pwd
                                    . s0ix-venv/bin/activate
                                    python3 adl-diff-s0ix-blocking-status.py ${plat} ${lpm_file} ${lpm_file2}
                                    deactivate
                                """
                            } else if (params.platform == 'ADL-P') {
                                //def lpm_vars = sh returnStdout: true, script: "grep \'LPM_STATUS_[0-5]\' ${lpm_file} | grep -oP \'LPM_STATUS_[0-5]:\\s+\\K.*\' | paste -sd \',\' -"
                                //echo "lpm_vars = ${lpm_vars}"
                                
                                sh """
                                    pwd
                                    . s0ix-venv/bin/activate
                                    python3 adl-diff-s0ix-blocking-status.py ${plat} ${lpm_file} ${lpm_file2}
                                    deactivate
                                """
                            } else if (params.platform == 'MTL') {
                                sh """
                                    pwd
                                    . s0ix-venv/bin/activate
                                    python3 mtl-s0ix-blocking-status.py ${lpm_file}
                                    deactivate
                                """
                            }
                        }
                    }
                }
            }
        }
    }
}
