pipeline {
    agent {
        label 'jenkins-master'
    }
    
    environment {
        PATH="${env.HOME}/bin/depot_tools:${env.PATH}"
    }
    
    parameters {
        choice choices: ['rex', 'brya', 'brask', 'nissa', 'dedede'], description: 'Select Platform Board', name: 'board'
        string defaultValue: 'R122', description: 'OS Image Release/Milestone', name: 'release', trim: true
        string defaultValue: '15753.0.0', description: 'OS Image Version/Prefix', name: 'version', trim: true
    }

    stages {
        stage('Download Image') {
            steps {
                cleanWs()
                dir("${env.HOME}/Downloads/${params.release}-${params.version}-${params.board}/") {
                    script {
                        if (fileExists("${params.release}-${params.version}-${params.board}.tar.xz")) {
                            echo "CrOS Image ${params.release}-${params.version}-${params.board} already exists"
                        } else {
                            echo "Downloading Image ${params.release}-${params.version}-${params.board}.tar.xz"
                    
                            sh "gsutil.py -D -o Credentials:gs_service_key_file=${env.HOME}/moblab-oauth.json ls gs://chromeos-moblab-intel/${params.board}-release/${params.release}-${params.version}/"
                            sh "gsutil.py -D -o Credentials:gs_service_key_file=${env.HOME}/moblab-oauth.json cp gs://chromeos-moblab-intel/${params.board}-release/${params.release}-${params.version}/chromiumos_test_image.tar.xz ${params.release}-${params.version}-${params.board}.tar.xz"
                        }
                    }
                }
            }
        }
        
        stage('Uncompress Image') {
            when { expression { return !fileExists("${env.HOME}/Downloads/${params.release}-${params.version}-${params.board}/chromiumos_test_image.bin") } }
            steps {
                echo "Uncompress Image ${params.release}-${params.version}-${params.board}.tar.xz"
                dir("${env.HOME}/Downloads/${params.release}-${params.version}-${params.board}/") {
                    sh "tar xf ${params.release}-${params.version}-${params.board}.tar.xz -I pixz"
                }
            }
            
            //post {
                //success {
                    //archiveArtifacts artifacts: "${params.release}-${params.version}-${params.board}/chromiumos_test_image.bin", fingerprint: true, followSymlinks: false, onlyIfSuccessful: true
                //}
            //}
        }
    }
}
