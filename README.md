## :hospital: Health Information User

> "Health information User" refers to an entity that wishes to consume the
>  services of the Health Data Consent Manager and obtain aggregated health
>  information for providing services to the Customer.

## :muscle: Motivation

> The customer may designate an HIU as the recipient of the Health Information when
> requesting consent artifact creation to consent manager. The HIU needs to maintain the
> customer’s health information provided to it securely in compliance with terms of the
> consent granted by the Customer.

## Build Status

[![Build](https://github.com/ProjectEKA/health-information-user/workflows/HIU%20master%20build/badge.svg)](https://github.com/ProjectEKA/hip-service/actions)

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
To run

```
./gradlew bootRun
```

or if you want to run in dev environment setup
```
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## Running The Tests

To run the tests
```
./grdlew test
```

## Features

1.  Consent Management
2.  Aggregate Health Information

## API Contract

Once ran the application, navigate to

```alpha
{HOST}/index.html
```