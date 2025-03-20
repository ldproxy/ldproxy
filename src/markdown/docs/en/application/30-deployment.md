# Deployment

ldproxy is published as OCI image on [Docker Hub](https://hub.docker.com/r/iide/ldproxy/). To deploy ldproxy you should be able to use any OCI compatible runtime, recommended and regularly tested are Docker and Kubernetes with containerd.

## Data Volume

The directory `/data` in the container is where ldproxy reads and writes deployment-specific files from and to by default.

It is declared as a volume in the image, which means it is supposed to exist outside of the container file system. If no mount for `/data` is provided, the container runtime normally will create an anonymous volume.

### Content

A minimal data directory will only contain a `tmp` directory for temporary files which might be deleted if necessary when ldproxy is stopped.

By default, the data directory also acts as the main [Store Source](20-configuration/10-store-new.md). In that case it may contain a `cfg.yml` with [global configuration settings](20-configuration/README.md) as well as `entities`, `values` and `resources` directories.

## Docker

To deploy ldproxy, you will need an installation of Docker. Docker is available for Linux, Windows and Mac. You will find detailed installation guides for each platform [here](https://docs.docker.com/).

### Installing and starting ldproxy

To install ldproxy, just run the following command on a machine with Docker installed:

```bash
docker run -d -p 7080:7080 -v ldproxy_data:/data iide/ldproxy:latest
```

This will download the latest stable ldproxy image, deploy it as a new container, make the web application available at port 7080 and save your application data in a Docker provided directory outside of the container.

Instead of using a Docker provided directory where ldproxy will store its data (i.e. "ldproxy_data) you may specify an absolute path, for example:

```bash
docker run --name ldproxy -d -p 7080:7080 -v ~/docker/ldproxy_data:/data iide/ldproxy:latest
```

We additionally added `--name ldproxy` to change the name of the docker container from a random name to "ldproxy".

You may also change the host port or other parameters to your needs by adjusting the commands shown on this page.

To check that the docker process is running, use

```bash
docker ps
```

which should return something similar to

```bash
CONTAINER ID        IMAGE                 COMMAND                  CREATED             STATUS              PORTS                    NAMES
62db022d9bee        iide/ldproxy:latest   "/ldproxy/bin/ldproxy"   16 minutes ago      Up 16 minutes       0.0.0.0:7080->7080/tcp   ldproxy
```

Check that ldproxy is running by opening the URI http://localhost:7080/ in a web browser. Since the ldproxy Manager will only be available in a future version, you should receive a `404` error.

If ldproxy is not responding, consult the log with `docker logs ldproxy`.

### Updating ldproxy

To update ldproxy, just remove the container and create a new one with the run command as above. For example:

```bash
docker stop ldproxy
docker rm ldproxy
docker run --name ldproxy -d -p 7080:7080 -v ~/docker/ldproxy_data:/data iide/ldproxy:latest
```

Your data is saved in a volume, not in the container, so your configurations, API resources and caches will still be there after the update.

## Kubernetes

Coming soon.
