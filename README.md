# Meerge cat

![Build and Test](https://github.com/vaslabs/meerge-cat/workflows/Scala%20CI/badge.svg)

![logo](https://github.com/vaslabs/reviewer-bot/raw/master/images/reviewerbot_256x256.png)

A bot that reviews PRs of other bots and merges them automatically if they passed build


## Status
This is a work in progress

### Usage

#### Dry run: Display all PRs that can be auto-merged
```
docker run -e BITBUCKET_TOKEN vaslabs/reviewer:0.0.2\
     dry-run\
     --username ${BITBUCKET_USERNAME}\
     --password-env BITBUCKET_TOKEN\
     --api-uri https://api.bitbucket.org/2.0
 ```
 #### Merge all: successful PRs successful build
 ```
docker run -e BITBUCKET_TOKEN vaslabs/reviewer:0.0.2\
     merge-all\
     --username ${BITBUCKET_USERNAME}\
     --password-env BITBUCKET_TOKEN\
     --api-uri https://api.bitbucket.org/2.0
 ```
## Goals
1. Integrate bitbucket
2. Integrate gitlab
3. Integrate github

### For first release

Pass the name of the author and credentials. The bot will scan bitbucket and try to merge all the PRs where status of type build is successful

### Second release

Plan after first release
