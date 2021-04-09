FROM java:8-jdk-alpine
RUN apk update && \
	  apk upgrade
VOLUME /tmp
EXPOSE 9090
ADD target/pdfbookmark-0.0.1-SNAPSHOT.jar pdfbkmark.jar
ENTRYPOINT ["java", "-jar", "/pdfbkmark.jar"]
