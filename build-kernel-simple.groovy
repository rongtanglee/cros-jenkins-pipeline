def product_name = ''
def family_name = ''
def current_dt = ''
def kernel_release = ''
def kernel_package = ''
def kernel_version = ''
def prev_local_branch = ''
def build_agent = ''

pipeline {
    agent any
    
    parameters {
        choice choices: ['brya', 'brask', 'rex', 'nissa', 'dedede'], description: 'Choose Reference Board', name: 'board'
        choice choices: ['v5.10', 'v5.15', 'v6.1', 'v6.6', 'upstream'], description: 'Linux kernel version', name: 'kernel_version'
    }

    stages {
        stage('Validate Build Parameters') {
            steps {
                script {
                    if (params.kernel_version == 'v5.10') {
                        kernel_package = 'chromeos-kernel-5_10'
                    } else if (params.kernel_version == 'v5.15') {
                        kernel_package = 'chromeos-kernel-5_15'
                    } else if (params.kernel_version == 'v6.1') {
                        kernel_package = 'chromeos-kernel-6_1'
                    } else if (params.kernel_version == 'v6.6') {
                        kernel_package = 'chromeos-kernel-6_6'
                    } else {
                        kernel_package = "chromeos-kernel-${params.kernel_version}"
                    }
                    echo "kernel_package = ${kernel_package}"
                    
                    if (params.board == 'brya' || params.board == 'brask') {
                        build_agent = 'cros-brya-d12'
                    } else {
                        build_agent = "cros-${params.board}-d12"
                    }
                    
                    echo "build_agent = ${build_agent}"

                }
            }
        }
        
        stage('Build Kernel Image') {
            agent {
                label "${build_agent}"
            }
            
            steps {
                script {            
                    dir("${env.HOME}/cros-tot") {
                        sh """
                            /home/ron/bin/depot_tools/cros_sdk --enter <<EOF
                            setup_board -b ${params.board}
                            cros workon --board ${params.board} start ${kernel_package}
                            emerge-${params.board} ${kernel_package}
                            <<-EOF
                        """
                    }
                    
                }
            }
        }
    }
}
