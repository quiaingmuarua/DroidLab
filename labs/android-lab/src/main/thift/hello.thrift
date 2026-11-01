namespace java com.demo.android.lab.sample.proto

struct User {
  1: i64 id,
  2: string name,
  3: optional i32 age
}

service HelloService {
  string ping(),
  User getUser(1:i64 id),
  void saveUser(1:User user)
}
