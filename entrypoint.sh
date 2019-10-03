#!/bin/bash

APP_HOME=/usr/app
OPT=""
JVM_OPT="-XX:+HeapDumpOnOutOfMemoryError"

if [[ -z ${OUTPUT_DIRECTORY} ]]; then
	OUTPUT_DIRECTORY=${APP_HOME}/output
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

if [[ -n ${TRUST_NODE} ]]; then
	OPT="$OPT --trust-node $TRUST_NODE"
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
        	OPT="$OPT -p ${argv[i+1]}"
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
        --trust-node)
        	OPT="$OPT --trust-node ${argv[i+1]}"
        	;;
        *)
            ;;
    esac
done

if [[ ${DB} == "remove" ]]; then
  rm -rf ${OUTPUT_DIRECTORY}
  echo "remove db success"
elif [[ ${DB} == "backup" ]]; then
  current_time=$(date "+%Y.%m.%d-%H.%M.%S")
  tar -czf ${OUTPUT_DIRECTORY}-${current_time}.tar.gz ${OUTPUT_DIRECTORY}
  rm -rf ${OUTPUT_DIRECTORY}
  echo "backup db success"
fi

echo "process    : java ${JVM_OPT} -jar ${APP_HOME}/${APP}.jar -c ${APP_HOME}/config/${CONFIG_FILE} -d ${OUTPUT_DIRECTORY} ${OPT}"
echo "application: $APP"
echo "db path    : $OUTPUT_DIRECTORY"
echo "config     : $CONFIG_FILE"

java ${JVM_OPT} -jar ${APP_HOME}/${APP}.jar -c ${APP_HOME}/config/${CONFIG_FILE} -d ${OUTPUT_DIRECTORY} ${OPT}