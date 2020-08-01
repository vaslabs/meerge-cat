---
layout: docs
title:  "Bitbucket support"
position: 1
---

## Description

This is under-development with a private repos first approach. The use-case
is that a bot with its own credentials has access and submits pull requests
to your private repo. A more generic functionality will be supported later.

Scans the pull requests of the given username and then for each one
if a status type build is successful it triggers a merge action. If
on dry-run it will just display the merge link without triggering the merge.


## Properties 

| name | description | example | 
|---|---|---|
| username | The username of the bot that creates the PRs | scala_steward_vaslabs |
| password-env | The environment variable where the bitbucket token of the above user can be found | BITBUCKET_API_TOKEN |
| api-uri | The bitbucket api base uri | https://api.bitbucket.org/2.0

## Examples

### Dry run
```sh
docker run -e BITBUCKET_TOKEN vaslabs/meerge-cat:0.0.7\
     dry-run\
     --username ${BITBUCKET_USERNAME}\
     --password-env BITBUCKET_TOKEN\
     --api-uri https://api.bitbucket.org/2.0
```

### Merge
```sh
docker run -e BITBUCKET_TOKEN vaslabs/meerge-cat:0.0.7\
     merge-all\
     --username ${BITBUCKET_USERNAME}\
     --password-env BITBUCKET_TOKEN\
     --api-uri https://api.bitbucket.org/2.0
```