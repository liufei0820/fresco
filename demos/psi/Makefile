build:
	cd ../../core && mvn clean install -DskipTests
	cd ../../suite/tinytables && mvn clean install -DskipTests
	cd ../common && mvn clean install -DskipTests
	mvn clean install -DskipTests
	mkdir -p server1;
	mkdir -p server2;
	cp target/fresco-demo-psi.jar server1;
	cp target/fresco-demo-psi.jar server2;

runPrePro:
	cd server1 && java -jar fresco-demo-psi.jar -e SEQUENTIAL_BATCHED -i 1 -l INFO -p 1:localhost:8081 -p 2:localhost:8082 -s tinytablesprepro -in 2,3,4,5,8,9,14 -key abc123abc123abc123abc123abc123ab > log.txt 2>&1 &
	cd server2 && java -jar fresco-demo-psi.jar -e SEQUENTIAL_BATCHED -i 2 -l INFO -p 1:localhost:8081 -p 2:localhost:8082 -s tinytablesprepro -in 2,3,4,6,7,12,14 -key abc123abc123abc123abc123abc123ab 2>&1 | tee log.txt

run:
	cd server1 && java -jar fresco-demo-psi.jar -e SEQUENTIAL_BATCHED -i 1 -l INFO -p 1:localhost:8081 -p 2:localhost:8082 -s tinytables -in 2,3,4,5,8,9,14 -key abc123abc123abc123abc123abc123ab > log.txt 2>&1 &
	cd server2 && java -jar fresco-demo-psi.jar -e SEQUENTIAL_BATCHED -i 2 -l INFO -p 1:localhost:8081 -p 2:localhost:8082 -s tinytables -in 2,3,4,6,7,12,14 -key abc123abc123abc123abc123abc123ab 2>&1 | tee log.txt

runDummy:
	cd server1 && java -jar fresco-demo-psi.jar -e SEQUENTIAL_BATCHED -i 1 -l INFO -p 1:localhost:8081 -p 2:localhost:8082 -s dummyBool -in 2,3,4,5,8,9,14 -key abc123abc123abc123abc123abc123ab > log.txt 2>&1 &
	cd server2 && java -jar fresco-demo-psi.jar -e SEQUENTIAL_BATCHED -i 2 -l INFO -p 1:localhost:8081 -p 2:localhost:8082 -s dummyBool -in 2,3,4,6,7,12,14 -key abc123abc123abc123abc123abc123ab 2>&1 | tee log.txt
