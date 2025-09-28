
How to deploy?
- get the code either check out OR the source.zip
- run gradlew bootRun
- access http://localhost:8080/


Thought process
- use postman to run public APIs and get data to have some ideas of data structure.
- from APIs, come up with the downloading raw data phase.
- once data downloaded, run services to calculate metrics.

Custom metrix: 
Regulatory Complexity Score: Based on sentence length, legal terminology density, and cross-references



Issues
it seems the /api/versioner/v1/titles.json only returns 50 titles.
I thought it was because of the pagination but could not find any info about that.
So the app can only download 50 titles.
