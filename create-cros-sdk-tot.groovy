pipeline {
    agent {
        label "${build_agent}"
    }
    
    parameters {
        choice choices: ['rex', 'brya', 'brask', 'dedede', 'hatch', 'nissa', 'octopus', 'puff', 'volteer'], description: 'Platform Board', name: 'board'
        string defaultValue: 'cros-tot', description: 'Repository folder name', name: 'repo_folder'
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
                dir("${env.HOME}/${params.repo_folder}") {
                    sh 'pwd'
                    sh 'repo init -u https://chromium.googlesource.com/chromiumos/manifest.git --repo-url https://chromium.googlesource.com/external/repo.git'
                }
            }
        }
        
        stage('Generate local manifest') {
            steps {
                build job: 'generate-local-manifest', parameters: [string(name: 'branch', value: ''), string(name: 'ignore_file', value: 'ignore.txt'), string(name: 'ignore_edk2_file', value: 'ignore_edk2')]
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
                dir("${env.HOME}/${params.repo_folder}") {
                    sh "repo sync -j 8"
                }
            }
        }
        
        stage('Setup board') {
            when {
                expression { params.setup_board }
            }
            steps {
                dir("${env.HOME}/${params.repo_folder}") {
                    sh "repo start def --all"
                    sh "${env.HOME}/bin/depot_tools/cros_sdk --enter -- setup_board --board=${params.board}"
                }
            }
        }
    }
}
