#!/bin/bash

cd "$(dirname "$(readlink -f "$0")")"
source ./common.sh
# you can change AgentDemo to AkkaAgentDemo, see akka
runCmd "${JAVA_CMD[@]}" -cp "$(getClasspathWithoutTtlJar)" \
    "-javaagent:$(getTtlJarPath)=ttl.agent.logger:STDOUT" \
    com.alibaba.demo.ttl.agent.AgentDemo
