#!groovy

import java.util.regex.Pattern
import java.util.regex.Matcher

def leinProjectVersion(file) {
    return (file.text =~ /defproject\s+\S+\s+"([^"]+)"/)[0][1]
}

def buildPlugin(subdir) {
    version = leinProjectVersion(new File(subdir, "project.clj"))
    echo "${subdir} plugin version ${version}"

    // Build the plugin using a Docker image.
    sh "docker run --rm -v $(pwd)/${subidr}:/build -w /build clojure:alpine lein uberjar"
}

node('docker') {
    slackJobDescription = "job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})"
    try {
        // Get the git commit hash and descriptive version.
        gitCommit = sh(returnStdout: true, script: "git rev-parse HEAD").trim()
        descriptiveVersion = sh(returnStdout: true, script: "git describe --long --tags --dirty --always").trim()

        // Display the git commit and descriptive version.
        echo "git commit: ${gitCommit}"
        echo "descriptive version: ${descriptiveVersion}"

        // Build the pid service.
        stage('PID Service') {
            buildPlugin("pid-service")
        }

        // Build the repo service.
        stage('Repo Service') {
            buildPlugin("repo-service")
        }
    } catch (InterruptedException e) {
        currentBuild.result = "ABORTED"
        slackSend color: 'warning', message: "ABORTED: ${slackJobDescription}"
        throw e
    } catch (e) {
        currentBuild.result = "FAILED"
        sh "echo ${e}"
        slackSend color: 'danger', messagre: "FAILED: ${slackJobDescription}"
        throw e
    }
}
