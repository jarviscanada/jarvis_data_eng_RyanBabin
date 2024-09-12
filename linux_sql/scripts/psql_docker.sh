#! /bin/sh

# capture CLI arguments
cmd=$1
db_username=$2
db_password=$3

# start docker
sudo systemctl status docker || sudo systemctl start docker

#check container status
docker container inspect jrvs-psql
container_status=$?

# user switch case to handle create/stop/start options
case $cmd in
  create) 

    # check if the container is already created
    if [ $container_status -eq 0 ]; then
      echo 'Container already exists!'
      exit 1
    fi

    # check number of CLI arguments
    if [ $# -ne 3 ]; then
      echo 'Create requires username and password'
      exit 1
    fi

    # create container
    docker volume create pgdata

    # start the container
    docker run --name jrvs-psql
      -e POSTGRES_USER=$db_username
      -e POSGRES_PASSWORD=$db_password
      -d
      -v pgdata:/var/lib/postgresql/data
      -p 5432:5432
      postgres:9.6-alpine
    exit $?
    ;;

  start|stop)

  # check instance status; exit 1 if container has not been created
  if [ $container_status -ne 0 ]; then
    echo "Container has not been created yet!"
    exit 1
  fi

  # start or stop the container
  docker container $cmd jrvs-psql
  exit $?
  ;;

*)
  echo 'Illegal command'
  echo 'Commands: start | stop | create'
  exit 1
  ;;

esac



