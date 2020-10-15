## :hospital: Health Information User

> "Health information User" refers to an entity that wishes to consume the
>  services of the Health Data Consent Manager and obtain aggregated health
>  information for providing services to the Customer.

## :muscle: Motivation

> HIU Service acts as requester of patient's health information. When consent is granted, it needs to manage and maintain the
> health information provided  in secure and safe manner andd in compliance with terms of the
> consent granted by the patient.

## Build Status

[![Build](https://github.com/ProjectEKA/health-information-user/workflows/HIU%20master%20build/badge.svg)](https://github.com/ProjectEKA/health-information-user/actions)

## :+1: Code Style

[JAVA Naming Conventions](https://google.github.io/styleguide/javaguide.html)

## :tada: Language/Frameworks

-   [JAVA](https://docs.microsoft.com/en-us/dotnet/csharp/language-reference/)
-   [spring webflux](https://docs.microsoft.com/en-us/aspnet/core/?view=aspnetcore-3.1)
-   [Easy Random](https://github.com/j-easy/easy-random)
-   [Vavr](https://www.vavr.io/vavr-docs/)

## :checkered_flag: Requirements

-   [docker >= 19.03.5](https://www.docker.com/)

## :whale: Running From The Docker Image

Create docker image

```
docker build -t hiu .
```

To run the image

```
docker run -d -p 8002:8080 hiu
```

## :rocket: Running From Source

### Database
clone the db initializer repo. Please see instructions in [hiu-db-initializer](https://github.com/ProjectEKA/hiu-db-initializer)
 
### To run

```
./gradlew bootRun
```

or if you want to run in local environment setup
```
CLIENT_SECRET=${CLIENT_SECRET} HFR_AFFINITY_DOMAINS=projecteka.in ./gradlew bootRun --args='--spring.profiles.active=local'
```
or 
```
CLIENT_SECRET=${CLIENT_SECRET} HFR_AFFINITY_DOMAINS=projecteka.in ./gradlew bootRunLocal
```
Note, in the above case, remember to set the **gatewayservice.clientId** appropriately in application*.yml  

## Running The Tests

To run the tests
```
./gradlew test
```

## Dev setup

Before you run the docker-compose command below, check your running containers, and modify the docker-compose-infra-lite.yml accordingly.  
The docker compose assumes that that you want to run a standalone HIU service.   
```
docker-compose -f docker-compose-infra-lite.yml up -d
CLIENTREGISTRY_CLIENTSECRET=${CLIENTREGISTRY_CLIENTSECRET} ./gradlew bootRun --args='--spring.profiles.active=local'
```

#### setup users
Create an admin user for HIU first. You need to create an admin account for HIU first. 
This is done manually by creating an entry in the “user” table. 

To do this, you need to hash the password for admin first. You can go to this [website](https://bcryptgenerator.com/), and encrypt a string (e.g. password) and with the generated hash string, create a user from postgres for database health_information_user

```
docker exec -it $(docker ps -aqf "name=^postgres$") /bin/bash
psql -U postgres
\c health_information_user
insert into "user" (username, password, role, verified) values ('admin', '$2a$04$WW.a3wKaiL2/7xWJc4jUmu4/55aJnwBJscZ.o18X.zLZcOdpwQGQa', 'ADMIN', true);
``` 

Now with the above admin user, you can create HIU users via API. 
To do that, you need to first authenticate the admin user and get a token.

```
curl --location --request POST 'http://localhost:8003/sessions' \
--header 'Content-Type: application/json' \
--header 'Content-Type: text/plain' \
--data-raw '{
    "username" : "admin", 
    "password" : "password"
}
'
``` 

Copy the bearer token from above and then you can make further API calls to create users. 

```
curl --location --request POST 'http://localhost:8003/users' \
--header 'Authorization: Bearer token-from-above-step' \
--header 'Content-Type: application/json' \
--data-raw '{
	"username" : "lakshmi",
	"password" : "password", 
	"role" : "DOCTOR",
	"verified": true
}'

```

Do check in the database, whether the user created in the above step is verified or not, and if not, set as verified.
```
docker exec -it $(docker ps -aqf "name=^postgres$") /bin/bash
psql -U postgres
\c health_information_user
select * from "user" where username = 'lakshmi';
update "user" set verified=true where username='lakshmi';
``` 

## API Contract

Once ran the application, navigate to below URL see the API doc. The service is now running

```alpha
http://localhost:8003/index.html
```
