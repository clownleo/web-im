/**
 * Created by jimily on 16-5-10.
 */

var utils = {
    randomStr: function (len) {
        var chars = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'];
        var res = "";
        Math.seed = new Date();
        for (var i = 0; i < len; i++) {
            var id = Math.ceil(Math.random() * 61);
            res += chars[id];
        }
        return res;
    }
};

var clientIO = function () {
    var client = io("ws://localhost:9090");
    var privateKey;
    var chatEncryptKeyArr = [];
    var chatDecryptKeyArr = [];
    var publicKeyArr = [];
    var randomURL = "https://www.random.org/integers/?num=10&min=1&max=1000000000&col=10&base=16&format=plain&rnd=new";
    var newStamp = function () {
        return Rx.Observable.create(function (sub) {
            client.emit("new stamp", function (code, stamp) {
                sub.onNext(stamp);
                sub.onCompleted();
            })
        });
    };
    var register = function (localhostRan, user) {
        var stream;
        if (localhostRan) {
            stream = Rx.Observable.just(utils.randomStr(80));
        } else {
            stream = Rx.Observable
                .fromPromise(
                $.ajax({
                    url: randomURL
                }).promise()
            )
                .map((s) => s.split('\t').join('').slice(0, -1));
        }
        return stream
            .map(function (s) {
                var publicKey = cryptico.publicKeyString(cryptico.generateRSAKey(s, 1024));
                //setPrivateKey(s);
                return {privateKey: s, publicKey: publicKey};
            })
            .map(function (data) {
                var key_encrypted = CryptoJS.encryptAES4Java(data.privateKey, user.pwd);
                var key_sign = SHA256(data.privateKey);
                return {
                    username: user.username,
                    key_encrypted: key_encrypted,
                    key_sign: key_sign,
                    pub_key: data.publicKey,
                    info: "",
                    rid: 123
                };
            })
            .doOnNext(function (data) {
                //TODO 将注册信息发送给服务器
                client.emit("register", data, function (result) {
                    if (result == 0) {
                        console.log("注册成功");
                        console.log("username:" + data.username);
                        console.log("key_encrypted:" + data.key_encrypted);
                        console.log("key_sign:" + data.key_sign);
                        console.log("pub_key:" + data.pub_key);
                    } else {
                        console.log(result);
                    }
                });
            });
    };
    var login = function (inputUser) {
        return Rx.Observable.zip(
            Rx.Observable
                .create(
                    subscriber => client.emit("get key encrypted", inputUser.username, function (code, data) {
                    if (code) {
                        subscriber.onError(data);
                        return;
                    }
                    console.log("get key encrypted:" + data);
                    subscriber.onNext(data);
                    subscriber.onCompleted();
                })),
            newStamp(),
            function (enk, stamp) {
                console.log("stamp:" + stamp);
                var key = CryptoJS.decryptAES4Java(enk, inputUser.pwd);
                privateKey = key;
                var key_sign = SHA256(key);
                console.log("key_sign:" + key_sign);
                return CryptoJS.encryptAES4Java(stamp, key_sign);
            }
        )
            .doOnNext(function (x) {
                console.log(x);
                client.emit("login" , {username:inputUser.username,signature:x} , function(data){
                    console.log(data);
                    if(data == 0){
                        console.log("login success");
                    }else{
                        privateKey = null;
                    }
                })
            });
    };
    var setPwdBck = function (inputUser) {
        return Rx.Observable
            .fromPromise(
            //TODO 从服务器获取stamp及MQA
            $.ajax({
                url: ""
            }).promise()
        )
            .map(function (user) {
                var key = cryptico.decryptAESCBC(user.MQA, MD5(inputUser.pwd));
                var ZQA = SHA256(key);
                var BQA = cryptico.encryptAESCBC(key, MD5(inputUser.pwd_bck));
                //TODO 设置密保是如何对称加密相应的传输内容
                var encryptStamp = cryptico.encryptAESCBC(user.stamp, MD5(ZQA));
                return {BQA: BQA, encryptStamp: encryptStamp};
            })
            .doOnNext(function (x) {
                //TODO 将加密后的内容传给服务器
            });
    };
    var setNewPwd = function (inputUser) {
        return Rx.Observable
            .fromPromise(
            //TODO 从服务器获取stamp及BQA
            $.ajax({
                url: ""
            }).promise()
        )
            .map(function (user) {
                var key = cryptico.decryptAESCBC(user.BQA, MD5(inputUser.pwd_bck));
                var ZQA = SHA256(key);
                var MQA = cryptico.encryptAESCBC(key, MD5(inputUser.newPwd));
                //TODO 重置密码是如何对称加密相应的传输内容
                var encryptStamp = cryptico.encryptAESCBC(user.stamp, MD5(ZQA));
                return {MQA: MQA, encryptStamp: encryptStamp};
            })
            .doOnNext(function (x) {
                //TODO 将加密后的内容传给服务器
            });
    };
    var sendMessage = function (message) {
        var stream;
        var chatKey = SHA256(privateKey + message.to_uid);
        //TODO 确认聊天密钥已在服务器有存储
        if (!chatEncryptKeyArr[message.to_uid]) {
            stream = Rx.Observable
                .just(function () {
                    var recvPublicKey = publicKeyArr[message.to_uid];
                    if (recvPublicKey) {
                        encrypChatKey = cryptico.encrypt(chatKey, recvPublicKey);
                        return encrypChatKey;
                    } else {
                        //TODO 从服务器获得接收方公钥，加密聊天密钥
                        $.ajax({
                            url: "",
                            success: function (data) {
                                setPublicKeyArr(message.to_uid, data.publicKey);
                                encrypChatKey = cryptico.encrypt(chatKey, data.publicKey);
                                return encrypChatKey;
                            }
                        })
                    }
                })
                .map(function (encrypChatKey) {
                    //TODO 将加密后的聊天密钥传给服务器
                    $.ajax({
                        url: ""
                    }).promise();
                })
                .map(() => {
                    chatEncryptKeyArr[message.to_uid] = true;
                })
                .just(chatKey);
        } else {
            stream = Rx.Observable.just(chatKey);
        }
        return stream
            .map(function (chatAESKey) {
                message.context = cryptico.encryptAESCBC(message.chat, MD5(chatAESKey));
            })
            .doOnNext(function () {
                //TODO 将message发送给服务器
            });
    };
    var recvMessage = function (message) {
        var chatKey = chatDecryptKeyArr[message.from_gid];
        var stream;
        if (!chatKey) {
            stream = Rx.Observable
                .fromPromise(
                //TODO 向服务器询问密钥
                $.ajax({
                    url: ""
                }).promise()
            )
                .map(function (enKey) {
                    var RSAKey = cryptico.generateRSAKey(privateKey, 1024);
                    chatKey = cryptico.decrypt(enKey, RSAKey);
                    chatDecryptKeyArr[message.from_gid] = chatKey;
                })
        } else {
            stream = Rx.Observable.just(chatKey);
        }
        return stream
            .map(function () {
                message.context = cryptico.decryptAESCBC(message.context, MD5(chatKey));
            })
            .just(message);
    };

    return {
        register: register,
        login: login,
        setPwdBck: setPwdBck,
        setNewPwd: setNewPwd,
        sendMessage: sendMessage,
        recvMessage: recvMessage
    }
};

var client = clientIO();
//client.register(true,{username:"abc",pwd:"abc"}).subscribe();
client.login({username: 'abc', pwd: 'abc'}).subscribe(()=>{},(error)=>console.log("error:"+error));

