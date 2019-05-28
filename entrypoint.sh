#!/bin/bash

APP_HOME=/usr/app
OPT=""
JVM_OPT="-XX:+HeapDumpOnOutOfMemoryError"

if [[ -z ${OUTPUT_DIRECTORY} ]]; then
	OUTPUT_DIRECTORY=${APP_HOME}/output-directory
fi

if [[ -z ${APP} ]]; then
	APP=FullNode
fi

if [[ -z ${CONFIG_FILE} ]]; then
	CONFIG_FILE=config.conf
fi

if [[ "$WITNESS" = "true" ]]; then
	OPT="$OPT --witness"
fi

if [[ -n ${PRIVATE_KEY} ]]; then
	OPT="$OPT -p $PRIVATE_KEY"
fi

argc=$#
argv=($@)

for (( i=0; i<argc; i++ )); do
    case ${argv[i]} in
        --net)
            NET=${argv[i+1]}
            ;;
        --app)
            APP=${argv[i+1]}
            ;;
        --config)
        	CONFIG_FILE=${argv[i+1]}
        	;;
        --pk)
        	PRIVATE_KEY=${argv[i+1]}
        	;;
        --witness)
        	OPT="$OPT --witness"
        	;;
        --db)
        	DB=${argv[i+1]}
        	;;
        --output-directory)
        	OUTPUT_DIRECTORY=${argv[i+1]}
        	;;
        *)
            ;;
    esac
done

if [[ ${DB} == "remove" ]]; then
  rm -rf ${OUTPUT_DIRECTORY}
  echo "remove db success"
elif [[ ${DB} == "backup" ]]; then
  echo "backup db success"
fi

echo "process    : java ${JVM_OPT} -jar ${APP_HOME}/${APP}.jar -c ${APP_HOME}/${CONFIG_FILE} -p ${PRIVATE_KEY} -d ${OUTPUT_DIRECTORY} ${OPT}"
echo "application: $APP"
echo "db path    : $OUTPUT_DIRECTORY"
echo "pk         : $PRIVATE_KEY"
echo "config     : $CONFIG_FILE"

java ${JVM_OPT} -jar ${APP_HOME}/${APP}.jar -c ${APP_HOME}/${CONFIG_FILE} -d ${OUTPUT_DIRECTORY} ${OPT}