/**
 * IM SDK
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

/**
 * socket.io-client Rx化支持
 */
(function () {
    var old_io = io;
    io = function () {
        var args = Array.prototype.slice.call(arguments, 0);
        var client = old_io.apply(this, args);

        client.rxEmit = function () {
            var args = Array.prototype.slice.call(arguments, 0);
            return Rx.Observable.create(function (subscriber) {
                args.push(function (code, data) {
                    if (code != 0) {
                        console.log("RxEmit error:" + code + ",data:" + data);
                        subscriber.onError({
                            code: code,
                            data: data
                        });
                        return;
                    }
                    subscriber.onNext(data);
                    subscriber.onCompleted();
                });

                client.emit.apply(client, args);
            });
        };

        var eventListening = {};

        client.rxOn = function (event) {
            var args = Array.prototype.slice.call(arguments, 0);
            if (eventListening[event])
                return eventListening[event];

            return eventListening[event] = Rx.Observable.create(function (subscriber) {
                client.on(event, function (data, ack) {
                    subscriber.onNext(data);
                    ack();
                });
            });
        };

        return client;
    }

})();


var clientIO = function () {
    var client = io("ws://localhost:9090", {
        rememberUpgrade: true
    });
    var privateKey;
    var chatEncryptKeyArr = [];
    var chatDecryptKeyArr = [];
    var publicKeyArr = [];
    var randomURL = "https://www.random.org/integers/?num=10&min=1&max=1000000000&col=10&base=16&format=plain&rnd=new";
    var messageType = {
        CHAT_MESSAGE : 1,
        ADD_FRIEND : 2,
        REPLY_ADD_FRIEND : 3,
        JOIN_GROUP : 4,
        REPLY_JOIN_GROUP : 5,
        DELETE_FRIEND : 6,
        DELETE_GROUP_MEMBER : 7,
        NOTIFICATION : 8
        };
    
    var IMEVENT = {
        LOGIN : "login",
        LOGOUT : "logout",
        REGISTER : "register",
        GET_KET_ENCRYPTED : "get key encrypted",
        GET_KET_ENCRYPTED_bck : "get key encrypted bck",
        MSG_SYNC : "msg_sync",
        NEW_STAMP : "new stamp",
        SET_KEY_BCK : "set key bck",
        RESET_KEY : "reset key",
        ADD_FRIEND : "add friend",
        REPLY_OF_ADD_FRIEND : "reply of add friend",
        REMOVE_FRIEND : "remove friend",
        SEND_TO_FRIEND : "send to friend",
        GET_FRIENDS : "get friends",
        JOIN_GROUP : "join group",
        REPLY_OF_JOIN_GROUP : "reply of join group",
        ADD_GROUP : "add group",
        GET_GROUP_INFO : "get group info",
        GET_CHAT_KEY : "get chat key",
        SET_CHAT_KEY : "set chat key",
        GET_PUBLIC_KEY : "get public key",
        GET_GROUPS : "get groups",
        GET_GROUP_MEMBERS : "get group members",
        EXIT_GROUP : "exit group",
        REMOVE_GROUP_MEMBER : "remove group member"
    };
    var newStamp = function () {
        return client.rxEmit(IMEVENT.NEW_STAMP);
    };
    var setAndGetChatKey = function (message) {
        if (!chatEncryptKeyArr[message.to_user]) {
            var chatKey = SHA256(privateKey + message.to_user);
            return getSbPublicKey(message.to_user)
                .map((publicKey) => cryptico.encrypt(chatKey, publicKey).cipher)
                .flatMap(chatKey_encrypt =>
                    client.rxEmit(IMEVENT.SET_CHAT_KEY, {to_user: message.to_user, chat_key: chatKey_encrypt})
                        .doOnNext(function () {
                            chatEncryptKeyArr[message.to_user] = chatKey;
                        }))
                .map(ignore => chatKey);
        } else {
            return Rx.Observable.just(chatEncryptKeyArr[message.to_user]);
        }

    };

    var GettingChatDecryptKeyArr = {};
    var getChatKey = function (fromUsername) {
        if (chatDecryptKeyArr[fromUsername]) {
            return Rx.Observable.just(chatDecryptKeyArr[fromUsername]);
        }
        if(GettingChatDecryptKeyArr[fromUsername])
            return GettingChatDecryptKeyArr[fromUsername];

        return GettingChatDecryptKeyArr[fromUsername] = client.rxEmit(IMEVENT.GET_CHAT_KEY, fromUsername)
            .map(function (chatKey_encrypt) {
                if(chatDecryptKeyArr[fromUsername])
                    return chatDecryptKeyArr[fromUsername];
                var RSAKey = cryptico.generateRSAKey(privateKey, 1024);
                var chatKey = cryptico.decrypt(chatKey_encrypt, RSAKey).plaintext;
                chatDecryptKeyArr[fromUsername] = chatKey;
                return chatKey;
            }).doOnNext(ignore => delete GettingChatDecryptKeyArr[fromUsername]);
    };
    var getSbPublicKey = function (username) {
        //TODO 以key_encrypt为密钥进行AES解密
        if (publicKeyArr[username]) {
            return Rx.Observable.just(publicKeyArr[username]);
        }
        return client.rxEmit(IMEVENT.GET_PUBLIC_KEY, username)
            .doOnNext(function (key) {
                publicKeyArr[username] = key;
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
            .flatMap(function (data) {
                //TODO 将注册信息发送给服务器
                return client.rxEmit(IMEVENT.REGISTER, data)
                    .doOnNext(function () {
                        console.log("register success");
                        console.log("username:" + data.username);
                        console.log("key_encrypted:" + data.key_encrypted);
                        console.log("key_sign:" + data.key_sign);
                        console.log("pub_key:" + data.pub_key);
                    })
            });
    };
    var login = function (inputUser) {
        return Rx.Observable.zip(
            client.rxEmit(IMEVENT.GET_KET_ENCRYPTED, inputUser.username),
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
            .flatMap(function (x) {
                console.log(x);
                return client.rxEmit(IMEVENT.LOGIN, {username: inputUser.username, signature: x})
                    .doOnNext(function () {
                        console.log("login success");
                    })
            });
    };
    var setPwdBck = function (inputUser) {
        return Rx.Observable.zip(
            client.rxEmit(IMEVENT.GET_KET_ENCRYPTED, inputUser.username),
            newStamp(),
            function (enk, stamp) {
                var key = CryptoJS.decryptAES4Java(enk, inputUser.pwd);
                var key_sign = SHA256(key);
                var key_encrypted_bck = CryptoJS.encryptAES4Java(key, inputUser.pwd_bck);
                //TODO 设置密保是如何对称加密相应的传输内容
                var encryptStamp = CryptoJS.encryptAES4Java(stamp, key_sign);
                return {username: inputUser.username, key_encrypted_bck: key_encrypted_bck, signature: encryptStamp};
            }
        )
            .flatMap(function (x) {
                console.log(x);
                return client.rxEmit(IMEVENT.SET_KEY_BCK, x)
                    .doOnNext(function () {
                        console.log("set key bck success");
                        privateKey = null;
                    })
            });
    };
    var setNewPwd = function (inputUser) {
        return Rx.Observable.zip(
            client.rxEmit(IMEVENT.GET_KET_ENCRYPTED_bck, inputUser.username),
            newStamp(),
            function (enKeyBck, stamp) {
                var key = CryptoJS.decryptAES4Java(enKeyBck, inputUser.pwd_bck);
                var key_sign = SHA256(key);
                var key_encrypted = CryptoJS.encryptAES4Java(key, inputUser.newPwd);
                //TODO 重置密码是如何对称加密相应的传输内容
                var encryptStamp = CryptoJS.encryptAES4Java(stamp, key_sign);
                return {username: inputUser.username, key_encrypted: key_encrypted, signature: encryptStamp};
            }
        )
            .flatMap(function (x) {
                console.log(x);
                return client.rxEmit(IMEVENT.RESET_KEY, x)
                    .doOnNext(function () {
                        console.log("reset key success");
                        privateKey = null;
                    })
            });
    };

    var logout = function () {
        privateKey = null;
        client.rxEmit(IMEVENT.LOGOUT).subscribe();
    };

    var addFriend = function (message) {
        return client.rxEmit(IMEVENT.ADD_FRIEND, message)
            .doOnNext(function () {
                console.log("add friend finish");
            });
    };

    var replyAddFriend = function (message) {
        return client.rxEmit(IMEVENT.REPLY_OF_ADD_FRIEND, message)
            .doOnNext(function () {
                console.log("add friend success");
            })
    };

    var sendMessage = function (message) {
        return setAndGetChatKey(message)
            .flatMap(function (chatKey) {
                message.context = CryptoJS.encryptAES4Java(message.context, chatKey);
                return client.rxEmit(IMEVENT.SEND_TO_FRIEND, message)
                    .doOnNext(function () {
                        console.log(message);
                        console.log("send success");
                    })
            })
    };
    var decryptMessage = function (message) {
        return getChatKey(message.from_user)
            .map(function (chatKey) {
                message.context = CryptoJS.decryptAES4Java(message.context, chatKey);
                return message;
            })
    };

    var onMsg = function () {
        return client
            .rxOn(IMEVENT.MSG_SYNC)
            .flatMap(function (message) {
                switch (message.type) {
                    case messageType.CHAT_MESSAGE:
                        return decryptMessage(message);
                    default :
                        return Rx.Observable.just(message);
                }
            });
    };

    var getFriendsList = function () {
        return client.rxEmit(IMEVENT.GET_FRIENDS);
    };

    var addGroup = function(group){
        return client.rxEmit(IMEVENT.ADD_FRIEND , group);
    };

    var joinGroup = function (group) {
        return client.rxEmit(IMEVENT.JOIN_GROUP , group);
    };

    var replyOfJoinGroup = function(message){
        return client.rxEmit(IMEVENT.REPLY_OF_JOIN_GROUP , message);
    };

    var getMembersOfGroup = function(groupName){
        return client.rxEmit(IMEVENT.GET_GROUP_MEMBERS , groupName);
    };

    var deleteFriend = function(friendName) {
        return client.rxEmit(IMEVENT.REMOVE_FRIEND , friendName);
    };

    var removeMemberOfGroup = function(removeGroupMember){
        return client.rxEmit(IMEVENT.REMOVE_GROUP_MEMBER , removeGroupMember);
    };

    var exitGroup = function(groupName){
        return client.rxEmit(IMEVENT.EXIT_GROUP , groupName);
    };

    var getListOfGroups = function(){
        return client.rxEmit(IMEVENT.GET_GROUPS);
    };

    return {
        messageType: messageType,
        register: register,
        login: login,
        setPwdBck: setPwdBck,
        setNewPwd: setNewPwd,
        logout: logout,

        sendMessage:sendMessage,
        onMsg: onMsg,

        addFriend: addFriend,
        replyAddFriend: replyAddFriend,
        getFriendsList: getFriendsList,
        deleteFriend:deleteFriend,
        removeMemberOfGroup:removeMemberOfGroup,
        exitGroup:exitGroup,
        getListOfGroups:getListOfGroups,

        addGroup:addGroup,
        joinGroup:joinGroup,
        replyOfJoinGroup:replyOfJoinGroup,
        getMembersOfGroup:getMembersOfGroup
    }
};

var client = clientIO();
client.onMsg().subscribe(data => console.log(data));
//client.register(true,{username:"abc",pwd:"abc"}).subscribe(()=>{},(error)=>{});
//client.login({username: 'abc', pwd: 'abc'}).subscribe(()=>{},(error)=>console.log("error:"+error.code));
//client.setPwdBck({username: 'abc', pwd: 'abc',pwd_bck:'臭包'}).subscribe(()=>{},(error)=>console.log("error:"+error));
//client.setNewPwd({username: 'abc', newPwd: '123',pwd_bck:'臭包'}).subscribe(()=>{},(error)=>console.log("error:"+error));
//client.login({username: 'abc', pwd: 'abc'}).subscribe(()=>{},(error)=>console.log("error:"+error.code));
//client.login({username: 'abc', pwd: '123'}).subscribe(()=>{},(error)=>console.log("error:"+error.code));

//client.addFriend({from_user:'abc' , to_user:'kiss'}).subscribe();
//client.replyAddFriend({from_user:'kiss' , to_user:'abc',context:'YES'}).subscribe();

//client.register(true,{username:"kiss",pwd:"abc"}).subscribe(()=>{},(error)=>{});
//client.login({username: 'kiss', pwd: 'abc'}).subscribe(()=>{},(error)=>console.log("error:"+error.code));

//client.getFriendsList().subscribe((data)=>{console.log(data)})
//client.deleteFriend('abc').subscribe((data)=>console.log(data))
//client.getSbPublicKey('abc').subscribe((data)=>{console.log(data)})

//client.sendMessage({from_user:'abc',to_user:'kiss',context:'你好'}).subscribe((data)=>{console.log(data)})


//client.addGroup({groupName:'smilence',info:'第一个群'}).subscribe();
//client.joinGroup({from_user:'kiss',to_user:'abc',group:'smilence'}).subscribe();
//client.replyOfJoinGroup({group:'smilence',to_user:'kiss',context:'YES'}).subscribe();
//client.getMembersOfGroup('smilence').subscribe();

//client.removeMemberOfGroup({group:'smilence',member:'kiss'}).subscribe((data)=>console.log(data));
//client.exitGroup('smilence').subscribe((data)=>console.log(data);
//client.getListOfGroups().subscribe((data)=>console.log(data));
