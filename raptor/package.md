## Building this version
- bash get_hsl_files.sh (this is tho get current hsl data, but will not rebuild the graph.obj)
- mvn clean package -Dskiptests (actual building)
- java -Xmx8G -jar otp-shaded\target\otp-shaded-2.10.0-SNAPSHOT.jar --build --save .\hsl (this is if the graph.obj needs to be updated)

## Running it
- java -Xmx8G -jar otp-shaded\target\otp-shaded-2.10.0-SNAPSHOT.jar --load .\hsl

## Digitransit-ui
docker run -p 8080:8080 -it -e CONFIG=hsl -e OTP_URL=http://192.168.178.27:9080/otp/ -e MAP_URL=https://cdn.digitransit.fi -e NODE_OPTS=--max_old_space_size=1500 -e GEOCODING_BASE_URL=https://api.digitransit.fi/geocoding/v1 -e GEOCODING_API_SUBSCRIPTION_QUERY_PARAMETER_NAME=digitransit-subscription-key -e GEOCODING_API_SUBSCRIPTION_HEADER_NAME=digitransit-subscription-key -e GEOCODING_API_SUBSCRIPTION_TOKEN=63aa748c4b994123b62784d466fb3ae5 -e API_SUBSCRIPTION_QUERY_PARAMETER_NAME=digitransit-subscription-key -e API_SUBSCRIPTION_HEADER_NAME=digitransit-subscription-key -e API_SUBSCRIPTION_TOKEN=63aa748c4b994123b62784d466fb3ae5 hsldevcom/digitransit-ui:v3-prod