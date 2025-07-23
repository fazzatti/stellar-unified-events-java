docker run --rm -it \
  -p 8000:8000 \
  -p 8001:8001 \
  -p 8004:8004 \
  stellar/quickstart:latest \
  --local \
  --enable horizon,rpc,friendbot 
