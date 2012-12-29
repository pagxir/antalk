JAR = bin/WireTalk.jar
MENIFEST = bin/MENIFEST

all: $(JAR)

$(MENIFEST):
	echo "Main-Class: wave.talk.UserConsole" > $(MENIFEST)
	echo "Class-path: " >> $(MENIFEST)

.PHONY: $(JAR)

$(JAR): $(MENIFEST)
	jar -cvmf $(MENIFEST) $(JAR) \
		-C bin/classes test \
		-C bin/classes wave

jar: $(JAR)

build:
	ant compile

install:
	ant install

uninstall:
	ant uninstall

clean:
	ant clean
