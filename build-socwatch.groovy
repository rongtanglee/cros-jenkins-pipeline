def kernel_package
def build_agent

pipeline {
    agent any
    
    parameters {
        choice choices: ['brya', 'brask', 'rex', 'nissa', 'dedede', 'brox'], description: 'Choose Reference Board', name: 'board'
        choice choices: ['v5.10', 'v5.15', 'v6.1', 'v6.6', 'upstream'], description: 'Linux kernel version', name: 'kernel_version'
        string defaultValue: '', description: 'Commit ID', name: 'Commit ID ($uname -r to check commit ID)'
        choice choices: ['socwatch_chrome_NDA_v2024.2.0_x86_64', 'socwatch_chrome_NDA_v2024.1.0_x86_64', 'socwatch_chrome_NDA_v2023.7.0_x86_64'], description: 'Select the socwatch version', name: 'socwatch_version'
    }

    stages {
        stage('Validate Build Parameters') {
            steps {
                script {
                    if (params.board == 'brya' || params.board == 'brask') {
                        build_agent = 'cros-brya-d12'
                    } else {
                        build_agent = "cros-${params.board}-d12"
                    }
                    
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
                    
                    echo "Build board:${params.board} kernel_version:${params.kernel_version} on commit:${params.commit_id}"
                }
            }
        }
        
        stage('Build kernel image') {

            steps {
                echo "Build kernel image"

                build job: 'build-kernel', parameters: [string(name: 'board', value: "${params.board}"), string(name: 'kernel_version', value: "${params.kernel_version}"), string(name: 'commit_id', value: "${params.commit_id}"), string(name: 'local_branch', value: 'build-socwatch'), , booleanParam(name: 'delete_local_branch', value: false)]
            }
        }
        
        stage('Build socwatch') {
            agent {
                label "${build_agent}"
            }
            
            steps {
                cleanWs()
                sh "cp -rfpv /mnt/jenkins/tools/${params.socwatch_version} ${env.WORKSPACE}"
                sh "cp -rfpv /mnt/jenkins/tools/socwatch_suspend ${env.WORKSPACE}/${params.socwatch_version}"
                script {
                    dir("${env.HOME}/cros-tot") {
                        sh """
                            /home/ron/bin/depot_tools/cros_sdk --enter <<EOF
                            cd /jenkins-workspace/build-socwatch/${params.socwatch_version}/socwatch_driver
                            make KERNEL_SRC_DIR=/build/${params.board}/var/cache/portage/sys-kernel/${kernel_package}/ clean
                            cd /jenkins-workspace/build-socwatch/${params.socwatch_version}/
                            ./build_drivers.sh -l -c /usr/bin/x86_64-cros-linux-gnu-clang -k /build/${params.board}/var/cache/portage/sys-kernel/${kernel_package}/
                            ./socwatch_chrome_create_install_package.sh 
                            <<-EOF
                        """
                    }
                }
            }
            
            post {
                always {
                    echo "Delete local branch"
                    dir("${env.HOME}/cros-tot/src/third_party/kernel/${params.kernel_version}") {
                        sh """
                            git checkout def
                            git branch -D build-socwatch
                        """
                    }
                }
                success {
                    dir("${env.WORKSPACE}/${params.socwatch_version}") {
                        archiveArtifacts artifacts: 'socwatch_chrome_CUSTOM.tar.gz', followSymlinks: false
                    }
                    
                }
            }
        }
    }
}
