# arguments
psql_host=$1
psql_port=$2
db_name=$3
psql_user=$4
psql_password=$5

if [ "$#" -ne 5 ]; then
  echo "Illegal number of parameters"
  exit 1
fi

# machine stats saved
lscpu_out=$(lscpu)
hostname=$(hostname -f)

# hardware specification variables
cpu_number=$(echo "$lscpu_out"  | egrep "^CPU\(s\):" | awk '{print $2}' | xargs)
cpu_architecture=$(echo "$lscpu_out" | grep "Architecture" | awk '{print $2}' | xargs)
cpu_model=$(echo "$lscpu_out" | grep "Model name" | awk -F[:] '{print $2}' | xargs)
cpu_mhz=$(echo "$lscpu_out" | grep "Model name" | awk -F[:@] '{printf "%.0f\n", $3 * 1000}' | xargs)
l2_cache=$(echo "$lscpu_out" | grep "L2" | awk '{print $3}')
total_mem=$(vmstat --unit M | tail -1 | awk '{print $4}')
timestamp=$(date '+%F %T')

# construction of insert statement
insert_stmt="INSERT INTO host_info(hostname, cpu_number, cpu_architecture, cpu_model, cpu_mhz, l2_cache, timestamp, total_mem) \
            VALUES('$hostname', '$cpu_number', '$cpu_architecture', '$cpu_model', '$cpu_mhz', '$l2_cache', '$timestamp', '$total_mem')"

# set up env var for psql cmd
export PGPASSWORD=$psql_password

psql -h $psql_host -p $psql_port -d $db_name -U $psql_user -c "$insert_stmt"
exit $?