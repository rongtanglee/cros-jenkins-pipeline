def board

pipeline {
    agent {
        label "${build_agent}"
    }
    
    parameters {
        choice choices: ['firmware-brya-14505.B', 'firmware-dedede-13606.B', 'firmware-hatch-12672.B', 'firmware-nissa-15217.B', 'firmware-octopus-11297.B', 'firmware-puff-133324.B', 'firmware-volteer-13672.B'], description: 'Firmware branch', name: 'fw_branch'
        string defaultValue: 'cros-fw', description: 'Repository folder name', name: 'repo_folder'
        booleanParam defaultValue: true, description: 'setup_board after syncing repository', name: 'setup_board'
    }
    
    environment {
        PATH="${env.HOME}/bin/depot_tools:${env.PATH}"
    }
    
    stages {
        stage('Repo init') {
            steps {
                echo 'Repo init CrOS ToT'
                sh 'printenv'
                echo "fw_branch: ${params.fw_branch}"
                echo "repo_folder: ${params.repo_folder}"
                echo "setup_board: ${params.setup_board}"
                script {
                    def (_fw, _board) = params.fw_branch.split('-')
                    board = _board
                    echo "board: ${board}"
                }
                
                dir("${env.HOME}/${params.repo_folder}") {
                    sh 'pwd'
                    sh "repo init -u https://chromium.googlesource.com/chromiumos/manifest.git --repo-url https://chromium.googlesource.com/external/repo.git -b ${params.fw_branch}"
                }
            }
        }
        
        stage('Generate local manifest') {
            steps {
                build job: 'generate-local-manifest', parameters: [string(name: 'branch', value: "${params.fw_branch}"), string(name: 'ignore_file', value: "ignore-${params.fw_branch}.txt"), string(name: 'ignore_edk2_file', value: "ignore_edk2-${params.fw_branch}")]
            }
            
            post {
                success {
                    echo "Copy local manifest to CrOS repository"
                    copyArtifacts filter: '*.xml', flatten: true, projectName: 'generate-local-manifest', selector: upstream(fallbackToLastSuccessful: true), target: "${env.HOME}/${params.repo_folder}/.repo/local_manifests"
                }
            }
        }
        
        stage('Repo sync') {
            steps {
                echo "Repo sync"
                dir("${env.HOME}/${params.repo_folder}") {
                    sh 'pwd'
                    sh "repo sync -j 8"
                    sh "repo start ${params.fw_branch} --all"
                }
            }
        }
        
        stage('Setup board') {
            when {
                expression { params.setup_board }
            }
            steps {
                echo "Download CrOS SDK and Setup ${board} Board"
                dir("${env.HOME}/${params.repo_folder}") {
                    sh 'pwd'
                    sh "cros_sdk --enter -- setup_board --board=${board}"
                }
            }
        }
    }
}
