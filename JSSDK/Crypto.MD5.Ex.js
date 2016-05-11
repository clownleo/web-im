/**
 * Created by leo on 16-5-11.
 */
CryptoJS.hash16bytes=function(str){
    if(typeof str != "string"){
        console.error("parameter must be a string");
        return ;
    }
    var hash=CryptoJS.MD5(str);
    hash.words[0]^=hash.words[2];
    hash.words[1]^=hash.words[3];
    hash.words = hash.words.slice(0,2);
    hash.sigBytes=8;
    return hash.toString();
};

CryptoJS.encryptAES4Java = function (word , key) {
    key = CryptoJS.hash16bytes(key);
    key = CryptoJS.enc.Utf8.parse(key);
    var iv  = CryptoJS.enc.Utf8.parse('0102030405060708');
    var srcs = CryptoJS.enc.Utf8.parse(word);
    var encrypted = CryptoJS.AES.encrypt(srcs, key, { iv: iv,mode:CryptoJS.mode.CBC});
    return encrypted.toString();
}

CryptoJS.decryptAES4Java = function (word , key) {
    key = CryptoJS.hash16bytes(key);
    key = CryptoJS.enc.Utf8.parse(key);
    var iv  = CryptoJS.enc.Utf8.parse('0102030405060708');
    var decrypt = CryptoJS.AES.decrypt(word, key, { iv: iv,mode:CryptoJS.mode.CBC});
    return CryptoJS.enc.Utf8.stringify(decrypt).toString();
}