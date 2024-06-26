def kernel_release
def kernel_package
def kernel_version
def prev_local_branch
def build_agent

pipeline {
    agent any
    
    parameters {
        choice choices: ['brya', 'brask', 'rex', 'nissa', 'dedede', 'brox'], description: 'Choose Reference Board', name: 'board'
        choice choices: ['v5.10', 'v5.15', 'v6.1', 'v6.6', 'upstream'], description: 'Linux kernel version', name: 'kernel_version'
        string defaultValue: '', description: 'Commit ID (checkout to this commit_id if specified)', name: 'commit_id'
        string defaultValue: 'remotes/m/main', description: 'Remote Branch (checkout to this remote branch if commit_id is not specified)', name: 'remote_branch'
        string defaultValue: 'local-branch', description: 'Local Branch', name: 'local_branch'
        booleanParam defaultValue: true, description: 'Delete the local branch once build completed', name: 'delete_local_branch'
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
                    
                    echo "Build board:${params.board} kernel_version:${params.kernel_version} on branch:${params.local_branch} commit:${params.commit_id}"
                }
            }
        }
        
        stage('Checkout local branch') {
            agent {
                label "${build_agent}"
            }
            
            steps {
                script {
                    sh 'printenv'
                    dir("${env.HOME}/cros-tot/src/third_party/kernel/${params.kernel_version}") {
                        // Get current local branch
                        prev_local_branch = sh returnStdout: true, script: 'git rev-parse --abbrev-ref HEAD'
                        echo "prev_local_branch = ${prev_local_branch}"
                        
                        if (!params.commit_id.isEmpty()) {
                            // Check if commit id exist
                            //sh "git branch --contains ${params.commit_id}"
                            
                            def commit_id_not_exist = sh returnStatus: true, script: "git checkout -b ${params.local_branch} ${params.commit_id}"
                            if (commit_id_not_exist != 0) {
                                echo "Commit ${params.commit_id} not exists"
                                currentBuild.result = 'ABORTED'
                                error("Commit ${params.commit_id} does not exist, abort ...")
                            }
                        } else {
                            // Check if remote branch exist
                            def checkout_branch = sh returnStatus: true, script: "git checkout -b ${params.local_branch} ${params.remote_branch}"
                            if (checkout_branch != 0) {
                                currentBuild.result = 'ABORTED'
                                error("Remote branch ${params.remote_branch} does not exist, abort ...")
                            }
                        }
                    }
                }
            }
            
            post {
                aborted {
                    dir("${env.HOME}/cros-tot/src/third_party/kernel/${params.kernel_version}") {
                        sh """
                            git checkout ${prev_local_branch}
                            git branch -D ${params.local_branch}
                        """
                    }
                }
                failure {
                    dir("${env.HOME}/cros-tot/src/third_party/kernel/${params.kernel_version}") {
                        sh """
                            git checkout ${prev_local_branch}
                            git branch -D ${params.local_branch}
                        """
                    }
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
                            exit
                            <<-EOF
                        """
                    }
                    
                }
            }
        }

        stage('Delete local branch') {
            when {
                expression { return (params.delete_local_branch) }
            }

            steps {
                dir("${env.HOME}/cros-tot/src/third_party/kernel/${params.kernel_version}") {
                    sh """
                        git checkout ${prev_local_branch}
                        git branch -D ${params.local_branch}
                    """
                }
            }
        }
    }
}
