#!/usr/bin/bash
pushd $(dirname -- "$0") 1> /dev/null
testCount=0
failCount=0
fail(){
    echo "Test failed: $1" 1>&2
    ((failCount++))
}
for testName in $(find -mindepth 1 -maxdepth 1 -type d); do
    ((testCount++))
    pushd $testName 1> /dev/null
    java -jar ../../target/pyjava-2.0.jar input/ --output output/
    if [ $? -ne 0 ]; then
        fail $testName
        continue
    fi
    diff --brief --recursive --text --ignore-trailing-space --ignore-blank-lines output/ expected/
    if [ $? -ne 0 ]; then
        rm -rf output/
        fail $testName
    fi
    rm -rf output/
    popd 1> /dev/null
done

echo "Ran $testCount tests: $((testCount - failCount)) passed, $failCount failed."