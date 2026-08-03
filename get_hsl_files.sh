BASE="https://api.digitransit.fi/routing-data/v3/hsl"
KEY="63aa748c4b994123b62784d466fb3ae5"

for FILE in HSL-gtfs.zip hsl.pbf HSLlautta-gtfs.zip build-config.json router-config.json; do
  curl -H "digitransit-subscription-key: $KEY" "$BASE/$FILE" -o "./hsl/$FILE"
done