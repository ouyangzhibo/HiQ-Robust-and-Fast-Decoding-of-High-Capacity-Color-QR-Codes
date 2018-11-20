function [ ] = Cross_Module_Intf_test( )
%  this script implements the cross-Module and cross-Channel interference
%  cancellation of color QR code decoding.
%  The model is :
%                       Y = C*X*M, 
%                           or 
%                       Y = f(X*M)
%  where C and M denote the cross-Channel and cross-Module interference
%  cancellation matrix, and X is the groundtruth and Y is the observed
%  color values.
%
%  Author: Zhibo Yang
%  Date: Sep. 02, 2016

%% prepare training data
load('./data/color_classification/highDen_data.mat');
gts = cell(size(data,1), 1);
modelString = 'quadratic';

% normalize color and reform labels
count = 1;
noiseNum = 4; 
aug_data = cell(size(data,1)*noiseNum, size(data,2));
aug_gts = cell(size(gts,1)*noiseNum, 1);
for i = 1 : size(data,1)
    obs = double(data{i,1});
    gt = data{i,2};
    white = zeros(3,1);
    for j = 1 : 3
        obs_j = obs(:,:,j);
        white(j) = mean(obs_j(gt==7));
        obs(:,:,j) = obs(:,:,j) / white(j);
    end
    temp = double(data{i,1});
    data{i,1} = obs;
    
    labels = data{i,2};
    gts{i} = labels;
    dim = size(labels, 1);
    gt = zeros(dim, dim, 3);
    gt(:,:,3) = (labels==1)|(labels==3)|(labels==5)|(labels==7);
    gt(:,:,2) = (labels==2)|(labels==3)|(labels==6)|(labels==7);
    gt(:,:,1) = (labels==4)|(labels==5)|(labels==6)|(labels==7);
    if strcmp(modelString, 'svm1') || strcmp(modelString, 'svm2')
        gt(gt==0) = -1;
    end
    data{i,2} = gt;
    
    % augment data with random noise injection
    aug_white = addNoise(white, noiseNum);
    for j = 1:noiseNum
        obs(:,:,1) = temp(:,:,1) / aug_white(j,1);
        obs(:,:,2) = temp(:,:,2) / aug_white(j,2);
        obs(:,:,3) = temp(:,:,3) / aug_white(j,3);
        aug_data{count, 1} = obs;
        aug_data{count, 2} = gt;
        aug_gts{count} = gts{i};
        count = count + 1;
    end
end
data = cat(1, data, aug_data);
gts = cat(1, gts, aug_gts);

trainNum = round(size(data, 1)*0.2);
perm = randperm(size(data,1))';
data = data(perm, :);
gts = gts(perm);
data_train = data(1:trainNum,:);
data_test = data(trainNum+1:end,:);
gt_train = gts(1:trainNum);
gt_test = gts(trainNum+1:end,:);


%% learning parameters
maxIter = 500;
istrain = 1;

if strcmp(modelString, 'linear')
    % initialization
    if istrain == 1
        Y = [];
        delta = 1e-3;
        for i = 1 : size(data_train,1)
            Y = cat(1, Y, reshape(permute(data{i,1},[3,2,1]), [], 1));
        end
        C = eye(3); % CCI cancellation matrix
                    % M is CMI cancellation matrix
        lastC = magic(3);
        lastM = zeros(5,1);
        for i = 1 : maxIter
            % estimate M
            MX = prepareCMI(data_train, C);
            M =  MX \ Y
            deltaM = norm(M - lastM, 1);
            lastM = M;
        %     M = [1,0,0,0,0]';
            % estimate C
            CX = prepareCCI(data_train, M);
            C = reshape(CX \ Y, 3, [])'
            deltaC = (C - lastC);
            deltaC = norm(deltaC(:), 1);
            lastC = C;

            % compute loss
            loss = computeLoss(data_train, C, M, Y);

            fprintf('index: %d deltaM = %f deltaC = %f loss = %f\n',i, deltaM, deltaC, loss);

            if (deltaM + deltaC) < delta
                break;
            end

            if i == maxIter
                disp('Warning: reaching the maximum number of iterations!');
            end
        end
    end
    
    M =[0.7671
        0.0557
        0.0174
        0.0261
        0.0217];
    C =[0.9179    0.3086   -0.0426
        0.3859    0.7648   -0.0453
        0.1479    0.3744    0.5127];
    invA = inv(formSolverMatrix(M, 117));
    load('invA.mat');

    %% validate
    err = 0;
    for i = 1 : size(data_test, 1)
        obsimg = data_test{i, 1};
        recimg = recover(obsimg, C, invA);
        errorMap = recimg ~= data_test{i, 2};
        errCurr = sum(errorMap(:)) / length(errorMap(:))
        err = err + errCurr;
    end
    err = err / (size(data_test, 1) - trainNum)
    
%     err = 0;
%     for i = 1 : size(data_test, 1)
%         obsimg = data_test{i, 1};
%         recimg = recover(obsimg, C, invA);
%         errorMap = recimg ~= data_test{i, 2};
%         errCurr = sum(errorMap(:)) / length(errorMap(:))
%         err = err + errCurr;
%     end
%     err = err / (size(data_test, 1) - trainNum)
elseif strcmp(modelString, 'quadratic')
    [acc_baseline, acc_lda] = computeBaselineQDA(data_train, gt_train, data_test, gt_test);
    fprintf('baseline accuracy = %f and LDA(3) accuracy = %f\n', acc_baseline, acc_lda);
    % initialization
    if istrain == 1
        delta = 1e0;
        M = [1,0,0,0,0];
        lastM = zeros(1,5);
        lastObjVal = 0;
        M = [0.9993, -0.0266, -0.0150, -0.0192, -0.0060];
        for i = 1 : maxIter
            % optimize \mu and \sigma
            [X, Y] = extractFeature(data_train, gt_train, M);
            model = fitcdiscr(X, Y, 'DiscrimType', 'quadratic');
            mu = model.Mu;
            sigma = model.Sigma;
            [objval, acc] = computeObjVal(data_train, gt_train, M, model);
            [~, testacc] = computeObjVal(data_test, gt_test, M, model);
            deltaObj = abs(objval - lastObjVal);
            lastObjVal = objval;

            % optimize M
            M = optSoluForM(data_train, gt_train, mu, sigma)';
            deltaM = norm(M - lastM, 1);
            lastM = M;

            fprintf('index: %d deltaM = %f trainACC = %f testACC = %f objval= %f\n',i, deltaM, acc, testacc, objval);

            if deltaObj < delta
                break;
            end

            if i == maxIter
                disp('Warning: reaching the maximum number of iterations!');
            end
        end
    end
    %% calculate DSR
    rstCMI = zeros(1,3);
    for j = 1 : size(data_test,1)
        [testFeature, ~] = extractFeature(data_test(j,1), [], M);
        [predictions, ~] = predict(model,testFeature);
        recImg = prediction2img(predictions, size(data_test{j,1},1));
        rstCMI = rstCMI + decode(recImg);
    end
    rstCMI
elseif strcmp(modelString, 'svm1') % Self-Designed Method
    
    layerIdx = 3;
    
    %% initialize
    C = 1;
    learning_rate = 5e-6;
    alpha = rand(countNumber(data_train),1)*C;
    XY = computeXY(data_train, layerIdx);
    %% training
    if istrain == 1
        delta = 1e-2;
        lastM = [1,0,0,0,0]';
        lastObj = inf;
        M = lastM;
        for i = 1 : maxIter
            alpMat = permute(repmat(alpha,[1,4,5]), [2,3,1]).*XY;
            A = sum(alpMat,3);
            %% update W and M
            % method 1
%             Sigma = A * A'; [V, D] = eig(Sigma); [maxEigVal, maxEigIdx] =
%             max(diag(D)); W = (sqrt(maxEigVal)/norm(V(:,maxEigIdx),2)) *
%             V(:,maxEigIdx); M = A' * W; M = M / norm(M);
            % method 2
            for j = 1 : maxIter
                W = A * M;
                M = A' * W;
                M = M / norm(M, 2);
                deltaM = norm(M - lastM, 1);
                lastM = M;
                if deltaM < delta
                    break;
                end
            end
            
            %% update alpha
            G = zeros(size(alpha));
            for j = 1 : length(G)
                G(j) = W' * XY(:,:,j) * M;
            end
            alpha = alpha - learning_rate / i * (G - 1);
            
            % threshold alpha to [0,C]
            alpha(alpha<0) = 0;
            alpha(alpha>C) = C;
            
            deltaM = norm(M - lastM, 1);
            lastM = M;
            
%             if mod(i, 1) == 0
            obj = (W' * W) / 2;
            for j = 1 : length(G)
                obj = obj + alpha(j) * (1 - W' * XY(:,:,j) * M);
            end
            alpha(1:10)'
            fprintf('%dth iter: objVal = %f, deltaM=%f, deltaObj=%f\n', i, obj, deltaM, abs(lastObj - obj));
            M', W'
            
            if abs((lastObj - obj)/lastObj) < 1e-4
                break;
            end
            lastObj = obj;
%             end

            if i == maxIter
                disp('Warning: reaching the maximum number of iterations!');
            end
        end
        [~,idx] = max(abs(M));
        if M(idx) < 0
            M = -M;
            W = -W;
        end
    end
    
    %% validate
    W = [0,0,0];
    M = [1,0,0,0,0]';
    compareMethodsSVM(data_train, data_test, layerIdx, W, M);
    
elseif strcmp(modelString, 'svm2') % extend existing SVM

    M_all = [0.9895   -0.0875   -0.0803   -0.0657   -0.0504
     0.9899   -0.0537   -0.0979   -0.0820   -0.0308
     0.9926   -0.0443   -0.0743   -0.0818   -0.0253]';
 
    M3_all = zeros(5, 3);
    M3_all(1,:) = 1;
    
    mapping = 'quadratic';
    [W_all, W3_all, W15_all] = calcAcc (data_train, data_test, mapping, M_all);
    W_all, W15_all, W3_all
    
    % compute the DSR for all methods
    rst3 = computeDSR (data_test, W3_all, [], 'feat3')
    rst15 = computeDSR (data_test, W15_all, [], 'feat15-2')
    rstCMI = computeDSR (data_test, W_all, M_all, 'CMI')
    
end

end


function [ W_all, W3_all, W15_all ] = calcAcc (data_train, data_test, mapping, M_all)
    featDim = 3;
    if strcmp (mapping, 'quadratic')
        featDim = 10;
    elseif strcmp (mapping, 'polynomial')
        featDim = 20;
    end
    W_all = zeros(featDim+1, 3);
    W3_all = zeros(featDim+1, 3);
    W15_all = [];
    for layerIdx = 1:3
        % initialization
        train_gt_layer = cvtDataByLayer(data_train, layerIdx);
        test_gt_layer = cvtDataByLayer(data_test, layerIdx);

        [X_train_raw, Y_train] = extractRawFeature(data_train, train_gt_layer, 'linear');
        [X_test_raw, Y_test] = extractRawFeature(data_test, test_gt_layer, 'linear');
        

        M = M_all(:, layerIdx);
        [acc_train, acc_test, W] = acc4RawOrCMI (M, X_train_raw, Y_train, X_test_raw, Y_test, mapping);
        fprintf('Layer %d: CMI training acc = %f, testing acc = %f\n', layerIdx, acc_train, acc_test);
        W_all(:,layerIdx) = W;
        
        [acc3_train, acc3_test, W3] = acc4RawOrCMI ([1, 0, 0, 0, 0]', X_train_raw, Y_train, X_test_raw, Y_test, mapping);
        fprintf('Layer %d: Feat3 training acc = %f, testing acc = %f\n', layerIdx, acc3_train, acc3_test);
        W3_all(:,layerIdx) = W3;
        
        [acc15_train, acc15_test, w15] = evaluateSVMFeat15_2(X_train_raw, Y_train, X_test_raw, Y_test, mapping);
        W15_all = cat(2, W15_all, w15);
        fprintf('Layer %d: Feat15 training acc = %f, testing acc = %f\n', layerIdx, acc15_train, acc15_test);

    end  
end

function [acc_train, acc_test, W] = acc4RawOrCMI ( M, X_train_raw, Y_train, X_test_raw, Y_test, mapping )
    
    trainNum = size(X_train_raw, 3);
    X_train = zeros(trainNum, 3);
    testNum = size(X_test_raw, 3);
    X_test = zeros(testNum, 3);
        
    for j = 1 : trainNum
        X_train(j, :) = M' * X_train_raw(:,:,j);
    end
    X_train = featureMapping(X_train, mapping);

    model = svmtrain(Y_train, X_train, '-t 0 -h 0 -c 1');
    W = calWeightsLibsvm(model);

    tempX = X_train;
    tempX(:,size(tempX,2)+1) = -1;
    scores_train = tempX*W;
    pred = double(scores_train > 0);
    pred(pred==0) = -1;
    acc_train = sum(pred==Y_train)/length(Y_train);

    for j = 1 : testNum
        X_test(j, :) = M' * X_test_raw(:,:,j);
    end
    X_test = featureMapping(X_test, mapping);

    tempX = X_test;
    tempX(:,size(tempX,2)+1) = -1;
    scores_test = tempX*W;
    pred = double(scores_test > 0);
    pred(pred==0) = -1;
    acc_test = sum(pred==Y_test)/length(Y_test);
    
end

function [ X ] = prepareCMI (data, C)
    if size(C,1) ~= 3 || size(C,2) ~= 3
        disp('invalid input C');
        return
    end
    X = [];
    for i = 1 : size(data,1)
%         Y = data{i,1};
        img = data{i,2};
        [height, width, ~] = size(img);
        Xi = zeros(height*width*3,5);
        img = padarray(img, [1, 1, 0], 1);
        j = 1;
        for m = 2 : height + 1
            for n = 2 : width+1
                Ai = C * [img(m,n,1), img(m,n,2), img(m,n,3);
                          img(m,n-1,1), img(m,n-1,2), img(m,n-1,3);
                          img(m,n+1,1), img(m,n+1,2), img(m,n+1,3);
                          img(m-1,n,1), img(m-1,n,2), img(m-1,n,3);
                          img(m+1,n,1), img(m+1,n,2), img(m+1,n,3)]';
                Xi(j*3-2:j*3,:) = Ai;
%                 M = M + inv(Ai'*Ai) * Ai' * reshape(Y(m-1,n-1,:),[],1);
                j = j + 1;
            end
        end
        X = cat(1, X, Xi);
    end
end

function [ X ] = prepareCCI (data, M)
    if size(M,1) ~= 5 || size(M,2) ~= 1
        disp('invalid input M');
        return
    end
    X = [];
    for i = 1 : size(data,1)
        img = data{i,2};
        [height, width, ~] = size(img);
        Xi = zeros(height*width*3,9);
        img = padarray(img, [1, 1, 0], 1);
        j = 1;
        for m = 2 : height + 1
            for n = 2 : width+1
                T = [img(m,n,1), img(m,n,2), img(m,n,3);
                       img(m,n-1,1), img(m,n-1,2), img(m,n-1,3);
                       img(m,n+1,1), img(m,n+1,2), img(m,n+1,3);
                       img(m-1,n,1), img(m-1,n,2), img(m-1,n,3);
                       img(m+1,n,1), img(m+1,n,2), img(m+1,n,3)]' * M;
                Xi(j*3-2:j*3,:) = [T', zeros(1, 6);
                                   zeros(1,3), T', zeros(1,3);
                                   zeros(1,6), T'];
                j = j + 1;
            end
        end
        X = cat(1, X, Xi);
    end
end

function [ A ] = formSolverMatrix ( weights, dim)
    num = dim * dim;
    A = zeros(num);
    for i = 1 : num
        A(i, i) = weights(1);
        if mod(i, dim) ~= 1
            A(i-1, i) = weights(2);
        end
        if mod(i, dim) ~= 0
            A(i+1, i) = weights(3);
        end
        if i > dim
            A(i-dim, i) = weights(4);
        end
        if i <= num - dim
            A(i+dim, i) = weights(5);
        end
    end
end

function [ Loss ] = computeLoss (data, C, M, Y)
    X = [];
    for i = 1 : size(data,1)
        img = data{i,2};
        [height, width, ~] = size(img);
        Xi = zeros(height*width*3,1);
        img = padarray(img, [1, 1, 0], 1);
        j = 1;
        
        for m = 2 : height + 1
            for n = 2 : width+1
                T = C * [img(m,n,1), img(m,n,2), img(m,n,3);
                           img(m-1,n,1), img(m-1,n,2), img(m-1,n,3);
                           img(m,n-1,1), img(m,n-1,2), img(m,n-1,3);
                           img(m,n+1,1), img(m,n+1,2), img(m,n+1,3);
                           img(m+1,n,1), img(m+1,n,2), img(m+1,n,3)]' * M;
                Xi(j*3-2:j*3) = T;
                j = j + 1;
            end
        end
        X = cat(1, X, Xi);
    end
    Loss = norm(X-Y, 2);
end

function [ recimg ] = recover ( obvimg, C, invA )
    % CM- and CC- interference cancellation
    Y = reshape(permute(obvimg,[2,1,3]), [], 3);
    YY = C \ Y';
    YY = YY * invA ;
    recimg = permute(reshape(YY', size(obvimg)),[2,1,3]);
    
    % local thresholding
    patchNum = 8;
    stride = floor(size(obvimg,1) / patchNum);
    for i = 1 : patchNum
        sIdx = i*stride - stride + 1;
        if i == patchNum
            eIdx = size(obvimg,1);
        else
            eIdx = i*stride;
        end
        for j = 1 : patchNum
            sIdx2 = j*stride - stride + 1;
            if j == patchNum
                eIdx2 = size(obvimg,1);
            else
                eIdx2 = j*stride;
            end
            for k = 1 : 3
                patch = recimg(sIdx:eIdx, sIdx2: eIdx2, k);
                threshold = (max(patch(:))+min(patch(:)))/2;
                recimg(sIdx:eIdx, sIdx2: eIdx2, k) = im2bw(patch, threshold);
            end
        end
    end
end

function [X, Y] = extractFeature (data, gts, M, mapping)
    X = []; 
    Y = [];
    for i = 1 : size(data,1)
        img = data{i,1};
        [height, width, ~] = size(img);
        Xi = zeros(height*width,3);
        img = padarray(img, [1, 1, 0], 1);
        j = 1;
        for n = 2 : width+1
            for m = 2 : height + 1
                Xi(j,:) = M * [img(m,n,1), img(m,n,2), img(m,n,3);
                       img(m,n-1,1), img(m,n-1,2), img(m,n-1,3);
                       img(m,n+1,1), img(m,n+1,2), img(m,n+1,3);
                       img(m-1,n,1), img(m-1,n,2), img(m-1,n,3);
                       img(m+1,n,1), img(m+1,n,2), img(m+1,n,3)];
                j = j + 1;
            end
        end
        X = cat(1, X, Xi);
    end
    
    if ~isempty(mapping)
        X = featureMapping (X, mapping);
    end
    
    if ~isempty(gts)
        if size(data,2) == 2
            for i = 1 : size(data,1)
                Y = cat(1, Y, reshape(gts{i}, [], 1));
            end
        end
    end
end

function [X, Y] = extractRawFeature (data, gts, mapping)
    X = []; 
    Y = [];
    for i = 1 : size(data,1)
        img = data{i,1};
        [height, width, ~] = size(img);
        Xi = zeros(5,3,height*width);
        img = padarray(img, [1, 1, 0], 1);
        j = 1;
        for n = 2 : width+1
            for m = 2 : height + 1
                Xi(:,:,j) = [img(m,n,1), img(m,n,2), img(m,n,3);
                       img(m,n-1,1), img(m,n-1,2), img(m,n-1,3);
                       img(m,n+1,1), img(m,n+1,2), img(m,n+1,3);
                       img(m-1,n,1), img(m-1,n,2), img(m-1,n,3);
                       img(m+1,n,1), img(m+1,n,2), img(m+1,n,3)];
                j = j + 1;
            end
        end
        X = cat(3, X, Xi);
    end
    
    if ~isempty(mapping)
        dim = size(featureMapping (X(1,:,1), mapping), 2);
        X_new = zeros(size(X,1), dim, size(X,3));
        for i = 1 : size(X, 3)
            X_new(:,:,i) = featureMapping (X(:,:,i), mapping);
        end
        X = X_new;
    end
    
    if ~isempty(gts)
        if size(data,2) == 2
            for i = 1 : size(data,1)
                Y = cat(1, Y, reshape(gts{i}, [], 1));
            end
        end
    end
end

function [ XY ] = computeXY( data, layerIdx )
    XY = [];
    for i = 1 : size(data,1)
        img = data{i,1};
        label = data{i,2};
        [height, width, ~] = size(img);
        Xi = zeros(4,5,height*width);
        img = padarray(img, [1, 1, 0], 1);
        j = 1;
        for n = 2 : width+1
            for m = 2 : height + 1
                Xi(:,:,j) = label(m-1, n-1, layerIdx) * ...
                       [img(m,n,1), img(m,n,2), img(m,n,3), -1;
                       img(m,n-1,1), img(m,n-1,2), img(m,n-1,3), -1;
                       img(m,n+1,1), img(m,n+1,2), img(m,n+1,3), -1;
                       img(m-1,n,1), img(m-1,n,2), img(m-1,n,3), -1;
                       img(m+1,n,1), img(m+1,n,2), img(m+1,n,3), -1]';
                j = j + 1;
            end
        end
        XY = cat(3, XY, Xi);
    end
end

function [num] = countNumber (data)
    num = 0;
    for i = 1 : size(data,1)
        num = num + numel(data{i,1})/size(data{i,1},3);
    end
end

function [ M ] = optSoluForM ( data, gts, mu, sigma )
    L= zeros(5);
    R = zeros(5,1);
    mu = mu';
    invSigma = sigma;
    for i = 1 : size(sigma, 3)
        invSigma(:,:,i) = inv(sigma(:,:,i));
    end

    for ii = 1 : size(data,1)
        img = data{ii,1};
        gt = gts{ii};
        [height, width, ~] = size(img);
        img = padarray(img, [1, 1, 0], 1);
        for n = 2 : width+1
            for m = 2 : height + 1
                cid = gt(m-1, n-1);
                sigma_ii = invSigma(:,:,cid+1);
                mu_i = mu(:, cid+1);
                xi = [img(m,n,1), img(m,n,2), img(m,n,3);
                       img(m,n-1,1), img(m,n-1,2), img(m,n-1,3);
                       img(m,n+1,1), img(m,n+1,2), img(m,n+1,3);
                       img(m-1,n,1), img(m-1,n,2), img(m-1,n,3);
                       img(m+1,n,1), img(m+1,n,2), img(m+1,n,3)];
                L = L + xi * sigma_ii * xi';
                R = R + xi * sigma_ii * mu_i;
            end
        end
    end
    M = L \ R;
end

function [ objval, acc ] = computeObjVal ( data, gts, M, model )
    [X, Y] = extractFeature(data, gts, M);
    [preds, scores] = predict(model,X);
    acc = sum(preds == Y) / length(Y);
    objval = -sum(log(scores(sub2ind(size(scores), 1:size(scores, 1), (Y+1)'))));
end

function [X, Y] = extractFeatureBF1 (data, gts, mapping)
    
    [data_raw, Y] = extractRawFeature(data, gts, mapping);
    X = reshape(permute(data_raw, [2,1,3]), numel(data_raw(:,:,1)), [])';
    
end

function [X, Y] = extractFeatureBF2 (data, gts, mapping)
    
    [data_raw, Y] = extractRawFeature(data, gts, 'linear');
    X = reshape(permute(data_raw, [2,1,3]), numel(data_raw(:,:,1)), [])';
    X = featureMapping(X, mapping);
    
end

function [ acc, drAcc, drRst ] = computeBaselineQDA (train, train_gt, test, test_gt)
    %3-dim
    [X, Y] = extractFeature(train, train_gt, [1,0,0,0,0], []);
    model = fitcdiscr(X, Y, 'DiscrimType', 'quadratic');
    % calculate DSR
    rst3 = zeros(1,3);
    for j = 1 : size(test,1)
        [test_X, ~] = extractFeature(test(j,1), [], [1,0,0,0,0]);
        [predictions, ~] = predict(model,test_X);
        recImg = prediction2img(predictions, size(test{j,1},1));
        rst3 = rst3 + decode(recImg);
    end
    rst3
    
    %15-dim
    rst15 = zeros(1,3);
    [X, Y] = extractFeatureBF(train, train_gt);
    model = fitcdiscr(X, Y, 'DiscrimType', 'quadratic');
    [testFeature, testGT] = extractFeatureBF(test, test_gt);
    [predictions, ~] = predict(model,testFeature);
    acc = sum(predictions == testGT) / length(testGT);
    % calculate DSR
    for j = 1 : size(test,1)
        [test_X, ~] = extractFeatureBF(test(j,1), []);
        [predictions, ~] = predict(model,test_X);
        recImg = prediction2img(predictions, size(test{j,1},1));
        rst15 = rst15 + decode(recImg);
    end
    rst15
    
    % 15-dim to 3-dim LDA
    drRst = zeros(1,3);
    [drX, mapping] = compute_mapping([Y X], 'LDA', 3);
    smallModel = fitcdiscr(drX, Y, 'DiscrimType', 'quadratic');
    drTestFeature = out_of_sample(testFeature, mapping);
    [predictions, ~] = predict(smallModel, drTestFeature);
    drAcc = sum(predictions == testGT) / length(testGT);
    % calculate DSR
    for j = 1 : size(test,1)
        [test_X, ~] = extractFeatureBF(test(j,1), []);
        drTestFeature = out_of_sample(test_X, mapping);
        [predictions, ~] = predict(smallModel, drTestFeature);
        recImg = prediction2img(predictions, size(test{j,1},1));
        drRst = drRst + decode(recImg);
    end
    drRst
end

function [ data_new ]  = cvtDataByLayer (data, layerIdx)
    data_new = data(:,2);
    for i = 1 : size(data,1)
        data_new{i} = data_new{i}(:,:,layerIdx);
    end
end

function [ acc, weights ] = evaluateSVMFeat15_1 ( layerIdx, mapping )
    train_gt_layer = cvtDataByLayer(data_train, layerIdx);
    test_gt_layer = cvtDataByLayer(data_test, layerIdx);
    [X_train_raw, Y_train] = extractRawFeature(data_train, train_gt_layer, mapping);
    [X_test_raw, Y_test] = extractRawFeature(data_test, test_gt_layer, mapping);

    X_train = reshape(permute(X_train_raw, [2,1,3]), numel(X_train_raw(:,:,1)), [])';
    X_test = reshape(permute(X_test_raw, [2,1,3]), numel(X_test_raw(:,:,1)), [])';
    
    model = svmtrain(Y_train, X_train, '-t 0 -h 0 -c 1');
    weights = calWeightsLibsvm(model);
    
    tempX = X_test;
    tempX(:,size(tempX,2)+1) = -1;
    scores = tempX*weights;
    pred = double(scores > 0);
    pred(pred==0) = -1;
    acc = sum(pred==Y_test)/length(Y_test);
end

function [ acc_train, acc_test, weights ] = evaluateSVMFeat15_2 ( X_train_raw, Y_train, X_test_raw, Y_test, mapping )
    
    X_train = reshape(permute(X_train_raw, [2,1,3]), numel(X_train_raw(:,:,1)), [])';
    X_test = reshape(permute(X_test_raw, [2,1,3]), numel(X_test_raw(:,:,1)), [])';
    X_train = featureMapping(X_train, mapping);
    X_test = featureMapping(X_test, mapping);
    
    model = svmtrain(Y_train, X_train, '-t 0 -h 0 -c 1');
    weights = calWeightsLibsvm(model);
    
    tempX = X_test;
    tempX(:,size(tempX,2)+1) = -1;
    scores = tempX*weights;
    pred = double(scores > 0);
    pred(pred==0) = -1;
    acc_test = sum(pred==Y_test)/length(Y_test);
    
    tempX = X_train;
    tempX(:,size(tempX,2)+1) = -1;
    scores = tempX*weights;
    pred = double(scores > 0);
    pred(pred==0) = -1;
    acc_train = sum(pred==Y_train)/length(Y_train);
end


function [ rst ] = computeDSR (test_set, Ws, Ms, modelString)

    rst = zeros(1,3);
    
    switch modelString
        case 'feat3' % 3-dim
            if size(Ws,1) == 4
                mapping = 'linear';
            elseif size(Ws,1) == 11
                mapping = 'quadratic';
            elseif size(Ws,1) == 21
                mapping = 'polynomial';
            end
                
            for j = 1 : size(test_set,1)
                dimension = size(test_set{j,1},1);
                [test_X, ~] = extractFeature(test_set(j,1), [], [1,0,0,0,0], mapping);
                test_X(:,size(test_X,2)+1) = -1;
                recImg = zeros(dimension, dimension, 3);
                for layerIdx = 1:3
                    W = Ws(:,layerIdx);
                    scores = test_X * W;
                    pred = double(scores > 0);
                    recImg(:,:,layerIdx) = reshape(pred, dimension, []);
                end
                recImg = uint8(recImg * 255);
                rst = rst + decode(recImg);
            end
        case 'feat15-1'
            if size(Ws,1) == 16
                mapping = 'linear';
            elseif size(Ws,1) == 51
                mapping = 'quadratic';
            end
            
            for j = 1 : size(test_set,1)
                dimension = size(test_set{j,1},1);
                [test_X, ~] = extractFeatureBF1(test_set(j,1), [], mapping);
                test_X(:,size(test_X,2)+1) = -1;
                recImg = zeros(dimension, dimension, 3);
                for layerIdx = 1:3
                    W = Ws(:,layerIdx);
                    scores = test_X * W;
                    pred = double(scores > 0);
                    recImg(:,:,layerIdx) = reshape(pred, dimension, []);
                end
                recImg = uint8(recImg * 255);
                rst = rst + decode(recImg);
            end
        case 'feat15-2'
            if size(Ws,1) == 16
                mapping = 'linear';
            elseif size(Ws,1) == 137
                mapping = 'quadratic';
            elseif size(Ws,1) == 363
                mapping = 'polynomial';
            end
            
            for j = 1 : size(test_set,1)
                dimension = size(test_set{j,1},1);
                [test_X, ~] = extractFeatureBF2(test_set(j,1), [], mapping);
                test_X(:,size(test_X,2)+1) = -1;
                recImg = zeros(dimension, dimension, 3);
                for layerIdx = 1:3
                    W = Ws(:,layerIdx);
                    scores = test_X * W;
                    pred = double(scores > 0);
                    recImg(:,:,layerIdx) = reshape(pred, dimension, []);
                end
                recImg = uint8(recImg * 255);
                rst = rst + decode(recImg);
            end
        case 'CMI' % CMI model
            if size(Ws,1) == 4
                mapping = 'linear';
            elseif size(Ws,1) == 11
                mapping = 'quadratic';
            elseif size(Ws,1) == 21
                mapping = 'polynomial';
            end
            
            for j = 1 : size(test_set,1)
                dimension = size(test_set{j,1},1);
                recImg = zeros(dimension, dimension, 3);
                [X_test_raw, ~] = extractRawFeature(test_set(j,1), [], 'linear');
                testNum = size(X_test_raw, 3);
                test_X = zeros(testNum, 3);
                for layerIdx = 1:3
                    M = Ms(:,layerIdx);
                    for k = 1 : testNum
                        test_X(k, :) = M' * X_test_raw(:,:,k);
                    end
                    test_X_new = featureMapping(test_X, mapping);
                    test_X_new(:,size(test_X_new,2)+1) = -1;
                    W = Ws(:,layerIdx);
                    scores = test_X_new * W;
                    pred = double(scores > 0);
                    recImg(:,:,layerIdx) = reshape(pred, dimension, []);
                end
                recImg = uint8(recImg * 255);
                rst = rst + decode(recImg);
            end
    end
end

function [ acc3, acc15, accCMI ] = compareMethodsSVM (train_set, test_set, layerIdx, W, M)
    train_gt_layer = cvtDataByLayer(train_set, layerIdx);
    test_gt_layer = cvtDataByLayer(test_set, layerIdx);
    
    %% 3-dim feature
    [X, Y] = extractFeature(train_set, train_gt_layer, [1,0,0,0,0]);
    ops.MaxIter = 3e5;
    model = svmtrain(X, Y, 'options', ops, 'kernel_function', 'linear');
    
    [testFeature, testGT] = extractFeature(test_set, test_gt_layer, [1,0,0,0,0]);
    predictions = [];
    patNum = 50;
    for patchIdx = 0:patNum-1
        step = ceil(size(testFeature, 1)/patNum);
        currData = testFeature(patchIdx*step+1:min(patchIdx*step+step,size(testFeature, 1)),:);
        predictions = cat(1, predictions, svmclassify(model, currData));
    end

    acc3 = sum(predictions == testGT) / length(testGT);
    
    %% 15-dim feature
    [X, Y] = extractFeatureBF(train, train_gt_layer);
    ops.MaxIter = 3e5;
    model = svmtrain(X, Y, 'options', ops, 'kernel_function', 'linear');
    
    [testFeature, testGT] = extractFeatureBF(test, test_gt_layer);
    predictions = [];
    patNum = 50;
    for patchIdx = 0:patNum-1
        step = ceil(size(testFeature, 1)/patNum);
        currData = testFeature(patchIdx*step+1:min(patchIdx*step+step,size(testFeature, 1)),:);
        predictions = cat(1, predictions, svmclassify(model, currData));
    end
    acc15 = sum(predictions == testGT) / length(testGT);
    
    accCMI = testCMISVM (test, test_gt_layer, W, M);
    
end

function [ acc ] = testCMISVM (test, test_gt_layer, W, M)
    [testFeature, testGT] = extractFeature(test, test_gt_layer, M');
    testFeature(:,4) = 1;
    scores = testFeature * W;
    predictions = scores > 0;
    testGT(testGT==-1) = 0;
    acc = sum(predictions == testGT) / length(testGT);
end

function [ weights ] = calWeightsLibsvm ( model )
%% this function calculates weights with the model trained using libsvm

    dim = size(model.SVs, 2);
    if isstruct(model) == 0
        weights = [0,0,0]';
        return;
    end
    weights = sum(repmat(model.sv_coef, 1, dim) .* model.SVs,1);
    weights = [full(weights), model.rho]';
end

function [recImg] = prediction2img(predictions, dimension)
    recImg = zeros(dimension, dimension,3);
    labels = reshape(predictions, dimension, []);
    recImg(:,:,3) = (labels==1)|(labels==3)|(labels==5)|(labels==7);
    recImg(:,:,2) = (labels==2)|(labels==3)|(labels==6)|(labels==7);
    recImg(:,:,1) = (labels==4)|(labels==5)|(labels==6)|(labels==7);
    recImg = uint8(recImg * 255);
end

function [rst] = decode(colorIm)
    import com.google.zxing.*;
    
    colorIm = padarray(colorIm, [3, 3, 0], 255);
    colorIm = imresize(colorIm, 10, 'nearest');
    imjava = im2java(colorIm);
    width = imjava.getWidth();
    decodeRst=client.j2se.DecoderTest.decodePureCQR(imjava.getBufferedImage());
    rst = zeros(1,3);
    for i = 1 : length(decodeRst)
        if ~isempty(decodeRst(i))
            rst(i) = 1;
        end
    end
end

function [ augment_Iw ] = addNoise ( Iw, smpNum )
% add Gaussian noise to each channel of the estimated white value independently

    augment_Iw = 3 * randn(smpNum,length(Iw));
    for i = 1:smpNum
        augment_Iw(i,:) = augment_Iw(i,:) + Iw';
    end
end