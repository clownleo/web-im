/**
 *
 * Created by jimily on 2016/5/8.
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

var sensitive = {
    privateKey: function () {
        var key;
        setPrivateKey = function (k) {
            key = k;
        };
        function getPrivateKey() {
            return key;
        }

        return getPrivateKey;
    },
    isServerhasKey: function () {
        var encryptKeyArr = [];
        setEncryptKeyArr = function (key, value) {
            encryptKeyArr[key] = value;
        };
        function getEncryptKey(key) {
            return encryptKeyArr[key];
        }

        return getEncryptKey;
    },
    decryptKey: function () {
        var chatDecryptKeyArr = [];
        setDecryptKeyArr = function (key, value) {
            chatDecryptKeyArr[key] = value;
        };
        function getDecryptKey(key) {
            return chatDecryptKeyArr[key];
        }

        return getDecryptKey;
    },
    publicKey: function () {
        var publicKeyArr = [];
        setPublicKeyArr = function (key, value) {
            publicKeyArr[key] = value;
        };
        function getPublicKey(key) {
            return publicKeyArr[key];
        }

        return getPublicKey;
    }
};


var encryption = {
    randomURL: "https://www.random.org/integers/?num=10&min=1&max=1000000000&col=10&base=16&format=plain&rnd=new",
    privateKey: sensitive.privateKey(),
    chatEncryptKey: sensitive.isServerhasKey(),
    chatDecryptKey: sensitive.decryptKey(),
    userPublicKey: sensitive.publicKey(),
    register: function (localhostRan, user) {
        var stream;
        if (localhostRan) {
            stream = Rx.Observable.just(utils.randomStr(80));
        } else {
            stream = Rx.Observable
                .fromPromise($.ajax({
                    url: encryption.randomURL
                }).promise()
            )
                .map((s) => s.split('\t').join('').slice(0, -1)
            )
            ;
        }
        stream
            .map(function (s) {
                var publicKey = cryptico.publicKeyString(cryptico.generateRSAKey(s, 1024));
                //setPrivateKey(s);
                return {privateKey: s, publicKey: publicKey};
            })
            .map(function (data) {
                var MQA = cryptico.encryptAESCBC(data.privateKey, MD5(user.pwd));
                var ZQA = SHA256(data.privateKey);
                return {MQA: MQA, ZQA: ZQA, user: user, PA: data.publicKey};
            })
            .subscribe(function (x) {
                //TODO 将注册信息发送给服务器
                console.log(x.MQA);
                console.log(x.ZQA);
                console.log(x.user);
                console.log(x.PA);
            })
    },
    login: function (inputUser) {
        Rx.Observable
            .fromPromise(
            //TODO 从服务器获取stamp及MQA
            $.ajax({
                url: ""
            }).promise()
        )
            .map(function (user) {
                var privateKey = cryptico.decryptAESCBC(user.MQA, MD5(inputUser.pwd));
                setPrivateKey(privateKey);
                var ZQA = SHA256(privateKey);
                //TODO 登录是否仅对stamp加密
                return cryptico.encryptAESCBC(user.stamp, MD5(ZQA));
            })
            .subscribe(function (x) {
                //TODO 将加密后的stamp发送给服务器
                //TODO 登录失败：setPrivateKey(null);
            })
    },
    setSecurity: function (inputUser) {
        Rx.Observable
            .fromPromise(
            //TODO 从服务器获取stamp及MQA
            $.ajax({
                url: ""
            }).promise()
        )
            .map(function (user) {
                var privateKey = cryptico.decryptAESCBC(user.MQA, MD5(inputUser.pwd));
                var ZQA = SHA256(privateKey);
                var BQA = cryptico.encryptAESCBC(privateKey, MD5(inputUser.pwd_bck));
                //TODO 设置密保是如何对称加密相应的传输内容
                var encryptStamp = cryptico.encryptAESCBC(user.stamp, MD5(ZQA));
                return {BQA: BQA, encryptStamp: encryptStamp};
            })
            .subscribe(function (x) {
                //TODO 将加密后的内容传给服务器
            })
    },
    setNewPwd: function (inputUser) {
        Rx.Observable
            .fromPromise(
            //TODO 从服务器获取stamp及BQA
            $.ajax({
                url: ""
            }).promise()
        )
            .map(function (user) {
                var privateKey = cryptico.decryptAESCBC(user.BQA, MD5(inputUser.pwd_bck));
                var ZQA = SHA256(privateKey);
                var MQA = cryptico.encryptAESCBC(privateKey, MD5(inputUser.newPwd));
                //TODO 重置密码是如何对称加密相应的传输内容
                var encryptStamp = cryptico.encryptAESCBC(user.stamp, MD5(ZQA));
                return {MQA: MQA, encryptStamp: encryptStamp};
            })
            .subscribe(function (x) {
                //TODO 将加密后的内容传给服务器
            })
    },
    sendMessage: function (message) {
        var stream;
        var chatKey = SHA256(encryption.privateKey() + message.to_uid);
        //TODO 确认聊天密钥已在服务器有存储
        if (!encryption.chatEncryptKey(message.to_uid)) {
            stream = Rx.Observable
                    .just(function () {
                        var recvPublicKey = encryption.userPublicKey(message.to_uid);
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
                    }
                )
                    .map(function (encrypChatKey) {
                        //TODO 将加密后的聊天密钥传给服务器
                        $.ajax({
                            url: ""
                        }).promise();
                    })
                    .map(() => setDecryptKeyArr(message.to_uid, true)
        )
        .
            just(chatKey)
        } else {
            stream = Rx.Observable.just(chatKey);
        }
        stream
            .map(function (chatAESKey) {
                message.context = cryptico.encryptAESCBC(message.chat, MD5(chatAESKey));
            })
            .subscribe(function () {
                //TODO 将message发送给服务器
            })
    },
    recvMessage: function (message) {
        var chatKey = encryption.chatDecryptKey(message.from_gid);
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
                    var RSAKey = cryptico.generateRSAKey(encryption.privateKey(), 1024);
                    chatKey = cryptico.decrypt(enKey, RSAKey);
                    setDecryptKeyArr(message.from_gid, chatKey);
                })
        } else {
            stream = Rx.Observable.just(chatKey);
        }
        stream
            .map(function () {
                message.context = cryptico.decryptAESCBC(message.context, MD5(chatKey));
            })
            .subscribe(() => {
            }
        )
        ;
        return message;
    }
};

function encryptor(key) {
    var dict = {};
    return function (context) {
        return key + context;
    }

}
function x(username, password) {
    var pubKeys = {};
    var hello = function(){
       console.log("hello");
    };
    client.login(username, password, function () {

    });
    client.chat(duifang, function(pub_key){
        pubKeys[duifang] = pub_key;
    });

    var dict = {a: 1};
    return {
        hello: hello
    }
}
var add = function(a,b) {
    return a+b;
};
var a_add = function(a, f){
    return function(b){
        return f(a,b);
    }
};

var e = encryptor("123");
e("text");

// encryption.register(true,{pwd:'jingru'});
// encryption.sendMessage({to_uid:10});
