# k8s

> [!CAUTION]
> Files found in this directory should be used with caution and NOT in a production environment! They are mainly for showcasing purposes. So, adjust them as necessary before applying them to your cluster.

This directory contains:
- a deployment script (`deploy-to-minikube.sh`)
- k8s manifest files

## Prerequisites

### minikube

Make sure the following `minikube` addons are enabled before using this deployment solution:

- ingress
- registry
- storage-provisioner

You can enable addons using the following command:

```shell
minikube addons enable <addon>
```

For further information, see: [minikube addon docs](https://minikube.sigs.k8s.io/docs/commands/addons/).

### build artifact

Since the script builds a new Docker image on the fly before using it in the deployment, make sure that the application has been built using the following command in the root directory of the project:

```shell
npm run build
```


## Usage

The script will install a single message broker instance to an already existing `minikube` cluster. In order to use it make sure the following environment variables are set:

| ENV VAR | DESCRIPTION |
|---------|-------------|
| AUTH_JWKS_URL | URL to obtain JWKS from. Using keycloak this has the pattern `<KEYCLOAK_BASE_URL>/realms/<YOUR_REALM>/protocol/openid-connect/certs`. |
| HUB_AUTH_ROBOT_ID | ID of the robot account to be used. Needs to exist on the central side (hub) at `https://auth.privateaim.net/`. |
| ROBOT_SECRET | Associated secret of the robot account. |
| NODE_MESSAGE_BROKER_HOST | Host to be used for the message broker. It will be accessible under `message-broker.<HOST>.nip.io`. |
| NAMESPACE | Namespace to be used within the minikube cluster. |

Set the following optional environment variables for further configuration:

| ENV VAR | DESCRIPTION |
|---------|-------------|
| HUB_BASE_URL | Base URL of the central side (hub). Defaults to `https://api.privateaim.net`. |
| HUB_AUTH_BASE_URL | Base URL of the central side's (hub) auth provider. Defaults to `https://auth.privateaim.net`. |

After that simply call the script with:
```shell
./deploy-to-minikube
```
