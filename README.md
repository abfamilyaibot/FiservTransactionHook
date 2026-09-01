# hook

Java 17 application for hook processing.


## install cbxhooks.jar and ra-commons.jar from resources folder to your local .m2 repository

Note: adjust your own file location accordingly.

mvn install:install-file \
  -Dfile=/home/betty-leung/Downloads/cbxrhooks.jar \
  -DgroupId=com.dep.integration \
  -DartifactId=cbxrhooks \
  -Dversion=1.0.0 \
  -Dpackaging=jar

mvn install:install-file \
  -Dfile=/home/betty-leung/Downloads/ra-commons-24.2.2.16.jar \
  -DgroupId=com.dep.integration \
  -DartifactId=ra-commons \
  -Dversion=24.2.2.16 \
  -Dpackaging=jar

mvn install:install-file \
  -Dfile=/home/betty-leung/Downloads/OF-TransportManager-24.2.2.16.jar \
  -DgroupId=com.dep.integration \
  -DartifactId=ra-transportmanager \
  -Dversion=24.2.2.16 \
  -Dpackaging=jar

mvn install:install-file \
  -Dfile=//home/betty-leung/Downloads/ra-commonentity-24.2.2.16.jar \
  -DgroupId=com.dep.integration \
  -DartifactId=ra-commonentity \
  -Dversion=24.2.2.16 \
  -Dpackaging=jar

## Modify MambuMultiBillPaymentProcessorTest keystorePath to your own file location accordingly.
The dep_Mambu_nonprod.jks is under the resources folder.

## Build and test

```bash
mvn clean install
```

## to test the prcoessor methods working:
Comment out <skipTests>true</skipTests> in pom.xml , then mvn build again
This will make connection to Mambu test env with both normal and async process method. 
You can see the difference in elapsed time, and the processor response json.
