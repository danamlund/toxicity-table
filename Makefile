
jar: compile
	jar cmf manifest toxicity-table.jar -C bin . -C . src

compile:
	mkdir -p bin
	javac -d bin src/*.java

zip: 
	mkdir toxicity-table
	cp -Rf src Makefile manifest README metrics.xml sorttable.js toxicity-table
	zip -r toxicity-table.zip toxicity-table
	rm -Rf toxicity-table

clean:
	rm -rf toxicity-table.jar bin test_checkstyle_output.xml test_checkstyle_output.html

test: jar
	java -jar checkstyle-7.5.1-all.jar -c metrics.xml \
	     -f xml \
	     -o test_checkstyle_output.xml \
	     `find test -wholename '*.java'`
	java -jar toxicity-table.jar test_checkstyle_output.xml > test_checkstyle_output.html
